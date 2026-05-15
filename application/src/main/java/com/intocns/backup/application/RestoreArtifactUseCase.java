package com.intocns.backup.application;

import com.intocns.backup.domain.exception.ArtifactNotFoundException;
import com.intocns.backup.domain.exception.ArtifactNotPurgedException;
import com.intocns.backup.domain.model.AuditEvent;
import com.intocns.backup.domain.model.AuditLog;
import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.AuditLogPort;
import com.intocns.backup.domain.port.BackupStoragePort;
import com.intocns.backup.domain.port.QuotaRepository;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class RestoreArtifactUseCase {

    private static final Logger log = LoggerFactory.getLogger(RestoreArtifactUseCase.class);

    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;
    private final QuotaRepository quotaRepository;
    private final AuditLogPort auditLogPort;

    public RestoreArtifactUseCase(ArtifactRepository artifactRepository,
                                  BackupStoragePort storagePort,
                                  QuotaRepository quotaRepository,
                                  AuditLogPort auditLogPort) {
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
        this.quotaRepository = quotaRepository;
        this.auditLogPort = auditLogPort;
    }

    @Transactional
    public void restore(UUID artifactId) throws IOException {
        BackupArtifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId));

        if (artifact.purgedAt() == null) {
            throw new ArtifactNotPurgedException(artifactId);
        }

        storagePort.restoreFromTrash(Path.of(artifact.storagePath()));

        if (!artifactRepository.clearPurged(artifactId)) {
            // 이미 다른 트랜잭션에서 복구됨 — 파일은 이미 이동됐으므로 경고만 남김
            log.warn("restoreArtifact clearPurged no-op artifactId={}", artifactId);
        }

        quotaRepository.addUsage(artifact.hospitalId(), artifact.sizeBytes());

        log.info("restoreArtifact artifactId={} hospitalId={} path={}",
                artifactId, artifact.hospitalId().cocode(), artifact.storagePath());
        auditLogPort.record(new AuditLog(
                UUID.randomUUID(), null, artifactId,
                artifact.hospitalId(), AuditEvent.ARTIFACT_RESTORED,
                Map.of("reason", "MANUAL_RESTORE"),
                Instant.now()
        ));
    }
}
