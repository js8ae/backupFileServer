package com.intocns.backup.domain.model;

public enum AuditEvent {
    UPLOAD_INITIATED,
    UPLOAD_COMPLETED,
    UPLOAD_ABORTED,
    UPLOAD_EXPIRED,
    ARTIFACT_EVICTED,
}
