package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.BackupArtifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository {
    BackupArtifact save(BackupArtifact artifact);
    Optional<BackupArtifact> findById(UUID id);
    List<BackupArtifact> findAllActive();
    List<BackupArtifact> findExpiredNotPurgedBefore(Instant threshold);
    void markPurged(UUID id, Instant purgedAt);
}
