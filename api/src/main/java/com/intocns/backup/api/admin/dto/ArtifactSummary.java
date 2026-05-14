package com.intocns.backup.api.admin.dto;

import com.intocns.backup.domain.model.BackupArtifact;

import java.time.Instant;
import java.util.UUID;

public record ArtifactSummary(
        UUID id,
        String type,
        String originalFilename,
        String storagePath,
        long sizeBytes,
        String sha256,
        Instant createdAt,
        Instant expiresAt,
        Instant purgedAt
) {
    public static ArtifactSummary from(BackupArtifact artifact) {
        return new ArtifactSummary(
                artifact.id(),
                artifact.type().name(),
                artifact.originalFilename(),
                artifact.storagePath(),
                artifact.sizeBytes(),
                artifact.sha256(),
                artifact.createdAt(),
                artifact.expiresAt(),
                artifact.purgedAt()
        );
    }
}
