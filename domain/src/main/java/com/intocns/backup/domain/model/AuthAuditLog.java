package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuthAuditLog(
    UUID id,
    String clientId,
    HospitalId hospitalId,  // nullable — 인증 실패 시 알 수 없음
    String ipAddress,
    AuthAuditResult result,
    Instant createdAt
) {}
