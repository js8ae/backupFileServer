package com.intocns.backup.api.upload.dto;

import com.intocns.backup.api.config.FlexibleInstantDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReportClientErrorRequest(
        UUID sessionId,
        @NotBlank String errorType,
        String errorMessage,
        Long byteOffset,
        Map<String, String> clientInfo,
        @NotNull @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant occurredAt
) {}
