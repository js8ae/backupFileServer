package com.intocns.backup.api.admin.dto;

import com.intocns.backup.domain.model.UploadSession;

import java.time.Instant;
import java.util.UUID;

public record SessionSummary(
        UUID id,
        String type,
        String originalFilename,
        long totalSize,
        long currentOffset,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
    public static SessionSummary from(UploadSession session) {
        return new SessionSummary(
                session.id(),
                session.type().name(),
                session.originalFilename(),
                session.totalSize(),
                session.currentOffset(),
                session.status().name(),
                session.expiresAt(),
                session.createdAt()
        );
    }
}
