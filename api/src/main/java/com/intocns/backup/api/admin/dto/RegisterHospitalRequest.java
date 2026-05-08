package com.intocns.backup.api.admin.dto;

import com.intocns.backup.api.config.FlexibleInstantDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

public record RegisterHospitalRequest(
        @Positive long cocode,
        @NotBlank String name,
        @NotNull @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant licenseStartAt,
        @NotNull @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant licenseEndAt,
        @Positive long maxStorageBytes
) {
}
