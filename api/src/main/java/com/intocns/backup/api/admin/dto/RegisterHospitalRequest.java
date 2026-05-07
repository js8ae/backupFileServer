package com.intocns.backup.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record RegisterHospitalRequest(
        @Positive long cocode,
        @NotBlank String name,
        @NotNull Instant licenseStartAt,
        @NotNull Instant licenseEndAt,
        @Positive long maxStorageBytes
) {
}
