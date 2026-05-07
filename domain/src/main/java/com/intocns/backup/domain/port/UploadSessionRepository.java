package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository {
    UploadSession save(UploadSession session);
    Optional<UploadSession> findById(UUID id);
    Optional<UploadSession> findByTusUploadUri(String tusUploadUri);
    void updateTusUploadUri(UUID id, String tusUploadUri);
    void updateOffset(UUID id, long offset);
    void updateStatus(UUID id, UploadStatus status);
    List<UploadSession> findExpiredBefore(Instant threshold);
}
