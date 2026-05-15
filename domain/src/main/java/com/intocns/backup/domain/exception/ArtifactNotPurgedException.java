package com.intocns.backup.domain.exception;

import java.util.UUID;

public class ArtifactNotPurgedException extends BackupDomainException {
    public ArtifactNotPurgedException(UUID artifactId) {
        super("Artifact is not in trash (purged_at is null): " + artifactId);
    }
}
