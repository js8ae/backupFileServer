package com.intocns.backup.api.admin.dto;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalQuota;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;

import java.time.Instant;
import java.util.List;

public record HospitalBackupSummary(
        long cocode,
        String name,
        boolean active,
        Instant licenseEndAt,
        boolean licenseValid,
        QuotaStats quota,
        TypeStats db,
        TypeStats file,
        int activeSessionCount,
        int trashCount
) {
    public record QuotaStats(long usedBytes, long limitBytes, double usedPercent) {}

    public record TypeStats(int count, long totalSizeBytes, Instant latestBackupAt) {}

    public static HospitalBackupSummary of(
            Hospital hospital,
            HospitalQuota quota,
            List<BackupArtifact> allArtifacts,
            List<UploadSession> allSessions) {

        Instant now = Instant.now();

        List<BackupArtifact> active = allArtifacts.stream()
                .filter(a -> a.purgedAt() == null)
                .toList();
        int trashCount = (int) allArtifacts.stream()
                .filter(a -> a.purgedAt() != null)
                .count();

        QuotaStats quotaStats = quota == null
                ? new QuotaStats(0, 0, 0.0)
                : new QuotaStats(
                        quota.usedBytes(),
                        quota.limitBytes(),
                        quota.limitBytes() > 0
                                ? Math.round(quota.usedBytes() * 1000.0 / quota.limitBytes()) / 10.0
                                : 0.0);

        return new HospitalBackupSummary(
                hospital.id().cocode(),
                hospital.name(),
                hospital.active(),
                hospital.licenseEndAt(),
                hospital.isLicenseValid(now),
                quotaStats,
                typeStats(active, BackupType.DB),
                typeStats(active, BackupType.FILE),
                (int) allSessions.stream()
                        .filter(s -> s.status() == UploadStatus.INITIATED
                                || s.status() == UploadStatus.UPLOADING)
                        .count(),
                trashCount
        );
    }

    private static TypeStats typeStats(List<BackupArtifact> artifacts, BackupType type) {
        List<BackupArtifact> typed = artifacts.stream()
                .filter(a -> a.type() == type)
                .toList();
        long totalSize = typed.stream().mapToLong(BackupArtifact::sizeBytes).sum();
        Instant latest = typed.stream()
                .map(BackupArtifact::createdAt)
                .max(Instant::compareTo)
                .orElse(null);
        return new TypeStats(typed.size(), totalSize, latest);
    }
}
