package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.AuditLogPort;
import com.intocns.backup.domain.port.BackupStoragePort;
import com.intocns.backup.domain.port.QuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QuotaRebalanceJob {

    private static final Logger log = LoggerFactory.getLogger(QuotaRebalanceJob.class);

    private final QuotaRepository quotaRepository;
    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;
    private final AuditLogPort auditLogPort;

    public QuotaRebalanceJob(QuotaRepository quotaRepository,
                             ArtifactRepository artifactRepository,
                             BackupStoragePort storagePort,
                             AuditLogPort auditLogPort) {
        this.quotaRepository = quotaRepository;
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
        this.auditLogPort = auditLogPort;
    }

    @Scheduled(cron = "0 0 1 * * *")  // 매일 새벽 1시
    @Transactional
    public void run() {
        Instant now = Instant.now();
        List<HospitalQuota> quotas = quotaRepository.findAll();

        int rebalanced = 0;
        int skipped = 0;

        for (HospitalQuota quota : quotas) {
            HospitalId hospitalId = quota.hospitalId();

            List<BackupArtifact> dbArtifacts = artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.DB);
            long dbReserved = dbArtifacts.stream().mapToLong(BackupArtifact::sizeBytes).sum();

            long fileLimit = quota.limitBytes() - dbReserved;
            if (fileLimit <= 0) {
                // DB 파일만으로 한도 초과 — FILE 정리 불가, 건너뜀
                log.warn("job=QuotaRebalance cocode={} skip=db_exceeds_limit db_reserved={} limit={}",
                        hospitalId.cocode(), dbReserved, quota.limitBytes());
                skipped++;
                continue;
            }

            List<BackupArtifact> fileArtifacts = artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.FILE);
            long currentFileUsed = fileArtifacts.stream().mapToLong(BackupArtifact::sizeBytes).sum();

            if (currentFileUsed <= fileLimit) {
                continue;
            }

            log.info("job=QuotaRebalance cocode={} file_used={} file_limit={} excess={}",
                    hospitalId.cocode(), currentFileUsed, fileLimit, currentFileUsed - fileLimit);

            for (BackupArtifact artifact : fileArtifacts) {
                if (currentFileUsed <= fileLimit) {
                    break;
                }
                try {
                    storagePort.moveToTrash(Path.of(artifact.storagePath()));
                    if (artifactRepository.markPurged(artifact.id(), now)) {
                        quotaRepository.subtractUsage(hospitalId, artifact.sizeBytes());
                        currentFileUsed -= artifact.sizeBytes();
                        auditLogPort.record(new AuditLog(
                            UUID.randomUUID(), null, artifact.id(), hospitalId,
                            AuditEvent.ARTIFACT_EVICTED,
                            Map.of("type", BackupType.FILE.name(),
                                   "size_bytes", String.valueOf(artifact.sizeBytes()),
                                   "reason", "QUOTA_REBALANCE"),
                            now
                        ));
                    }
                } catch (IOException e) {
                    log.error("job=QuotaRebalance cocode={} artifact_id={} error=move_to_trash_failed msg={}",
                            hospitalId.cocode(), artifact.id(), e.getMessage());
                }
            }
            rebalanced++;
        }

        if (rebalanced > 0 || skipped > 0) {
            log.info("job=QuotaRebalance done rebalanced={} skipped={}", rebalanced, skipped);
        }
    }
}
