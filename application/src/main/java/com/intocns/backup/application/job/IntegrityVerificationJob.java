package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.BackupStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public class IntegrityVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(IntegrityVerificationJob.class);

    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;

    public IntegrityVerificationJob(ArtifactRepository artifactRepository,
                                    BackupStoragePort storagePort) {
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
    }

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    public void run() {
        List<BackupArtifact> artifacts = artifactRepository.findAllActive();
        if (artifacts.isEmpty()) {
            return;
        }

        log.info("job=IntegrityVerification total={}", artifacts.size());

        int verified = 0;
        int corrupted = 0;
        int missing = 0;

        for (BackupArtifact artifact : artifacts) {
            Path path = Path.of(artifact.storagePath());
            try {
                String actual = storagePort.sha256(path);
                if (!actual.equals(artifact.sha256())) {
                    log.error("job=IntegrityVerification CORRUPTION_DETECTED artifact_id={} cocode={} path={} expected={} actual={}",
                            artifact.id(), artifact.hospitalId().cocode(),
                            artifact.storagePath(), artifact.sha256(), actual);
                    corrupted++;
                } else {
                    verified++;
                }
            } catch (IOException e) {
                log.error("job=IntegrityVerification FILE_MISSING artifact_id={} cocode={} path={} msg={}",
                        artifact.id(), artifact.hospitalId().cocode(), artifact.storagePath(), e.getMessage());
                missing++;
            }
        }

        log.info("job=IntegrityVerification done verified={} corrupted={} missing={}",
                verified, corrupted, missing);
    }
}
