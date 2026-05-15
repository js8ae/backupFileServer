package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

public interface BackupStoragePort {
    Path promoteToArtifacts(Path incomingDataPath, HospitalId hospitalId, BackupType type, String filename) throws IOException;
    void moveToTrash(Path artifactPath) throws IOException;
    void restoreFromTrash(Path artifactPath) throws IOException;
    String sha256(Path path) throws IOException;
    /** cutoff 이전 날짜의 trash 하위 디렉토리를 영구 삭제. 삭제된 디렉토리 수 반환 */
    int purgeTrash(LocalDate cutoff) throws IOException;
}
