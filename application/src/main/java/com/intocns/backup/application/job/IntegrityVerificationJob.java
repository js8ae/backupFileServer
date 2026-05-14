package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.JobExecutionLog;
import com.intocns.backup.domain.model.JobExecutionStatus;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.BackupStoragePort;
import com.intocns.backup.domain.port.JobExecutionLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IntegrityVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(IntegrityVerificationJob.class);
    private static final String JOB_NAME = "IntegrityVerificationJob";

    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;
    private final JobExecutionLogPort jobExecutionLogPort;

    public IntegrityVerificationJob(ArtifactRepository artifactRepository,
                                    BackupStoragePort storagePort,
                                    JobExecutionLogPort jobExecutionLogPort) {
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
        this.jobExecutionLogPort = jobExecutionLogPort;
    }

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    public void run() {
        Instant startedAt = Instant.now();
        try {
            var summary = execute();
            jobExecutionLogPort.record(new JobExecutionLog(
                    UUID.randomUUID(), JOB_NAME, startedAt, Instant.now(),
                    JobExecutionStatus.SUCCESS, summary, null
            ));
        } catch (Exception e) {
            log.error("job={} failed msg={}", JOB_NAME, e.getMessage(), e);
            jobExecutionLogPort.record(new JobExecutionLog(
                    UUID.randomUUID(), JOB_NAME, startedAt, Instant.now(),
                    JobExecutionStatus.FAILED, null, e.getMessage()
            ));
        }
    }

    private Map<String, String> execute() {
        List<BackupArtifact> artifacts = artifactRepository.findAllActive();
        if (artifacts.isEmpty()) {
            return Map.of("verified", "0", "corrupted", "0", "missing", "0");
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
        return Map.of(
                "verified", String.valueOf(verified),
                "corrupted", String.valueOf(corrupted),
                "missing", String.valueOf(missing)
        );
    }
}
