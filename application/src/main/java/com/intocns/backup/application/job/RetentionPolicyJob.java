package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.port.ArtifactRepository;
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

@Component
public class RetentionPolicyJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyJob.class);

    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;
    private final QuotaRepository quotaRepository;

    public RetentionPolicyJob(ArtifactRepository artifactRepository,
                              BackupStoragePort storagePort,
                              QuotaRepository quotaRepository) {
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
        this.quotaRepository = quotaRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
    @Transactional
    public void run() {
        Instant now = Instant.now();
        List<BackupArtifact> expired = artifactRepository.findExpiredNotPurgedBefore(now);
        if (expired.isEmpty()) {
            return;
        }

        log.info("job=RetentionPolicy expired_count={}", expired.size());

        int moved = 0;
        int failed = 0;
        for (BackupArtifact artifact : expired) {
            try {
                storagePort.moveToTrash(Path.of(artifact.storagePath()));
                quotaRepository.subtractUsage(artifact.hospitalId(), artifact.sizeBytes());
                artifactRepository.markPurged(artifact.id(), now);
                moved++;
            } catch (IOException e) {
                log.error("job=RetentionPolicy artifact_id={} cocode={} error=move_to_trash_failed msg={}",
                        artifact.id(), artifact.hospitalId().cocode(), e.getMessage());
                failed++;
            }
        }

        log.info("job=RetentionPolicy done moved={} failed={}", moved, failed);
    }
}
