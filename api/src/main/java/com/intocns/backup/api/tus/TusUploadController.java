package com.intocns.backup.api.tus;

import com.intocns.backup.application.AbortUploadUseCase;
import com.intocns.backup.application.HandlePatchUseCase;
import com.intocns.backup.application.InitiateUploadUseCase;
import com.intocns.backup.application.LinkTusUploadUseCase;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class TusUploadController {

    private final ChunkedUploadProtocol protocol;
    private final InitiateUploadUseCase initiateUpload;
    private final LinkTusUploadUseCase linkTusUpload;
    private final HandlePatchUseCase handlePatch;
    private final AbortUploadUseCase abortUpload;

    public TusUploadController(
            ChunkedUploadProtocol protocol,
            InitiateUploadUseCase initiateUpload,
            LinkTusUploadUseCase linkTusUpload,
            HandlePatchUseCase handlePatch,
            AbortUploadUseCase abortUpload) {
        this.protocol = protocol;
        this.initiateUpload = initiateUpload;
        this.linkTusUpload = linkTusUpload;
        this.handlePatch = handlePatch;
        this.abortUpload = abortUpload;
    }

    /**
     * Client starts an upload session, then POSTs to TUS to create an upload resource.
     * This endpoint: (1) creates our session, (2) delegates POST to tus-java-server,
     * (3) links the returned TUS URI to the session.
     */
    @PostMapping
    public ResponseEntity<Void> post(
            @RequestHeader("Upload-Type") String uploadType,
            @RequestHeader("Upload-Filename") String filename,
            @RequestHeader("Upload-Length") long uploadLength,
            @RequestHeader(value = "Upload-Sha256", required = false) String sha256,
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        BackupType type = BackupType.valueOf(uploadType.toUpperCase());
        UUID sessionId = initiateUpload.initiate(caller, type, filename, uploadLength, sha256);

        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));

        String tusLocation = response.getHeader("Location");
        if (tusLocation != null) {
            linkTusUpload.link(sessionId, tusLocation, caller);
        }

        response.setHeader("X-Session-Id", sessionId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create(tusLocation != null ? tusLocation : "")).build();
    }

    @PatchMapping("/**")
    public void patch(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String tusUploadUri = extractTusUploadUri(request);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
        handlePatch.handle(tusUploadUri, caller);
    }

    @RequestMapping(value = "/**", method = RequestMethod.HEAD)
    public void head(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public void options(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @DeleteMapping("/**")
    public void delete(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String tusUploadUri = extractTusUploadUri(request);
        abortUpload.abortByTusUri(tusUploadUri, caller);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @GetMapping("/**")
    public void get(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    private String extractTusUploadUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx.isEmpty() ? uri : uri.substring(ctx.length());
    }
}
