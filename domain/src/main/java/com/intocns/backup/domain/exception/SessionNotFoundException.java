package com.intocns.backup.domain.exception;

import java.util.UUID;

public class SessionNotFoundException extends BackupDomainException {
    public SessionNotFoundException(UUID sessionId) {
        super("Upload session not found: " + sessionId);
    }
}
