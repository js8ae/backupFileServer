package com.intocns.backup.infrastructure.protocol;

import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.http.HttpRequestWrapper;
import com.intocns.backup.domain.port.http.HttpResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.exception.TusException;
import me.desair.tus.server.upload.UploadInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Component
public class TusJavaServerAdapter implements ChunkedUploadProtocol {

    private final TusFileUploadService tusService;
    private final Path incomingRoot;

    public TusJavaServerAdapter(
            TusFileUploadService tusService,
            @Value("${backup.storage.incoming-root}") String incomingRoot) {
        this.tusService = tusService;
        this.incomingRoot = Path.of(incomingRoot);
    }

    @Override
    public void process(HttpRequestWrapper request, HttpResponseWrapper response) throws IOException {
        HttpServletRequest req = (HttpServletRequest) request.raw();
        HttpServletResponse res = (HttpServletResponse) response.raw();
        tusService.process(req, res);
    }

    @Override
    public Optional<Info> getUploadInfo(String uploadUri) throws IOException {
        try {
            UploadInfo info = tusService.getUploadInfo(uploadUri);
            if (info == null) {
                return Optional.empty();
            }
            long offset = info.getOffset() != null ? info.getOffset() : 0L;
            long length = info.getLength() != null ? info.getLength() : 0L;
            Map<String, String> metadata = info.getMetadata() != null
                    ? Map.copyOf(info.getMetadata()) : Map.of();

            return Optional.of(new Info(offset, length, !info.isUploadInProgress(), metadata));
        } catch (TusException e) {
            throw new IOException("Failed to get upload info: " + e.getMessage(), e);
        }
    }

    @Override
    public Path resolveDataPath(String uploadUri) {
        // tus-java-server 저장 레이아웃: {incoming-root}/uploads/{tusId}/data
        // uploadUri 예시: /files/5d6b6a35-86e2-4744-87bc-21ebe4b079f7
        String tusId = uploadUri.substring(uploadUri.lastIndexOf('/') + 1);
        return incomingRoot.resolve("uploads").resolve(tusId).resolve("data");
    }

    @Override
    public void deleteUpload(String uploadUri) throws IOException {
        try {
            tusService.deleteUpload(uploadUri);
        } catch (TusException e) {
            throw new IOException("Failed to delete upload: " + e.getMessage(), e);
        }
    }
}
