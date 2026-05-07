package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UploadSession(
    UUID id,
    HospitalId hospitalId,
    BackupType type,
    String originalFilename,
    long totalSize,
    long currentOffset,
    String expectedSha256,   // nullable — client 제공, 완료 시 검증
    String tusUploadUri,     // nullable — TUS POST 이후 연결됨
    UploadStatus status,
    Instant expiresAt,
    Instant createdAt
) {
    public boolean isCompleted() {
        return currentOffset == totalSize;
    }
}
