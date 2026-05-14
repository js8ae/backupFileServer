package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.AuditLog;

public interface AuditLogPort {
    void record(AuditLog entry);
}
