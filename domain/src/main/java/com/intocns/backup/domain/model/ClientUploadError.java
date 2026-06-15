package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ClientUploadError(
        UUID id,
        UUID sessionId,          // nullable
        HospitalId hospitalId,   // nullable
        String errorType,
        String errorMessage,
        Long byteOffset,
        Map<String, String> clientInfo,
        Instant occurredAt,
        Instant reportedAt
) {}
