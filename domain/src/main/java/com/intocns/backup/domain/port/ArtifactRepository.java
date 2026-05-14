package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository {
    BackupArtifact save(BackupArtifact artifact);
    Optional<BackupArtifact> findById(UUID id);
    List<BackupArtifact> findAllActive();
    List<BackupArtifact> findByHospitalId(HospitalId hospitalId);
    /** 해당 병원·타입의 비삭제 artifact를 오래된 순(ASC)으로 반환 */
    List<BackupArtifact> findByHospitalIdAndType(HospitalId hospitalId, BackupType type);
    List<BackupArtifact> findExpiredNotPurgedBefore(Instant threshold);
    /** @return true if actually marked (first time), false if already purged by another transaction */
    boolean markPurged(UUID id, Instant purgedAt);
}
