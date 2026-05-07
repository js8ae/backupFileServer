package com.intocns.backup.domain.exception;

import java.util.UUID;

public class IntegrityCheckFailedException extends BackupDomainException {
    public IntegrityCheckFailedException(UUID sessionId, String expected, String actual) {
        super("SHA-256 mismatch for session=%s expected=%s actual=%s".formatted(
                sessionId, expected, actual));
    }
}
