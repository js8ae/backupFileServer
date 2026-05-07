package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.UUID;

public record BackupArtifact(
    UUID id,
    HospitalId hospitalId,
    BackupType type,
    String storagePath,
    long sizeBytes,
    String sha256,
    Instant createdAt,
    Instant expiresAt,
    Instant purgedAt         // null이면 아직 유효
) {
    public boolean isPurged() {
        return purgedAt != null;
    }
}
