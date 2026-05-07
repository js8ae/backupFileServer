package com.intocns.backup.domain.model;

import java.time.Instant;

public record Hospital(
    HospitalId id,
    String name,
    Instant licenseStartAt,
    Instant licenseEndAt,
    long maxStorageBytes,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isLicenseValid(Instant now) {
        return active && !now.isBefore(licenseStartAt) && now.isBefore(licenseEndAt);
    }
}
