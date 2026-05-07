package com.intocns.backup.domain.exception;

public class CredentialNotFoundException extends BackupDomainException {
    public CredentialNotFoundException(String clientId) {
        super("Credential not found: clientId=" + clientId);
    }
}
