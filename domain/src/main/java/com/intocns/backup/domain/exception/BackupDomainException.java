package com.intocns.backup.domain.exception;

public class BackupDomainException extends RuntimeException {
    public BackupDomainException(String message) {
        super(message);
    }

    public BackupDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
