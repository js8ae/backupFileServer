package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.AuthAuditLog;

public interface AuthAuditPort {
    void record(AuthAuditLog entry);
}
