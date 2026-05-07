package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;

import java.io.IOException;
import java.nio.file.Path;

public interface BackupStoragePort {
    Path promoteToArtifacts(Path incomingDataPath, HospitalId hospitalId, BackupType type, String filename) throws IOException;
    void moveToTrash(Path artifactPath) throws IOException;
    String sha256(Path path) throws IOException;
}
