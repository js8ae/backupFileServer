package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.ClientUploadError;

public interface ClientUploadErrorLogPort {
    void record(ClientUploadError error);
}
