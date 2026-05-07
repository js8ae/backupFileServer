package com.intocns.backup.api.admin.dto;

import java.time.Instant;

public record UpdateHospitalRequest(
        String name,
        Instant licenseStartAt,
        Instant licenseEndAt,
        Long maxStorageBytes,
        Boolean active
) {
}
