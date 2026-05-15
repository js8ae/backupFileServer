package com.intocns.backup.domain.exception;

import java.util.UUID;

public class ArtifactNotFoundException extends BackupDomainException {
    public ArtifactNotFoundException(UUID artifactId) {
        super("Artifact not found: " + artifactId);
    }
}
