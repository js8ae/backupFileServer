package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLog(
    UUID id,
    UUID sessionId,       // nullable
    UUID artifactId,      // nullable
    HospitalId hospitalId,
    AuditEvent event,
    Map<String, String> detail,
    Instant createdAt
) {}
