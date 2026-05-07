package com.intocns.backup.domain.port;

import com.intocns.backup.domain.port.http.HttpRequestWrapper;
import com.intocns.backup.domain.port.http.HttpResponseWrapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public interface ChunkedUploadProtocol {

    void process(HttpRequestWrapper request, HttpResponseWrapper response) throws IOException;

    Optional<Info> getUploadInfo(String uploadUri) throws IOException;

    Path resolveDataPath(String uploadUri);

    void deleteUpload(String uploadUri) throws IOException;

    record Info(long offset, long length, boolean completed, Map<String, String> metadata) {}
}
