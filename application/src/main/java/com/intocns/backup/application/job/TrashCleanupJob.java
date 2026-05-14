package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.JobExecutionLog;
import com.intocns.backup.domain.model.JobExecutionStatus;
import com.intocns.backup.domain.port.BackupStoragePort;
import com.intocns.backup.domain.port.JobExecutionLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Component
public class TrashCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TrashCleanupJob.class);
    private static final String JOB_NAME = "TrashCleanupJob";

    private final BackupStoragePort storagePort;
    private final JobExecutionLogPort jobExecutionLogPort;
    private final int trashDays;

    public TrashCleanupJob(BackupStoragePort storagePort,
                           JobExecutionLogPort jobExecutionLogPort,
                           @Value("${backup.retention.trash-days:7}") int trashDays) {
        this.storagePort = storagePort;
        this.jobExecutionLogPort = jobExecutionLogPort;
        this.trashDays = trashDays;
    }

    @Scheduled(cron = "0 0 4 * * *")  // 매일 새벽 4시
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

    private Map<String, String> execute() throws IOException {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(trashDays);
        int deleted = storagePort.purgeTrash(cutoff);
        if (deleted > 0) {
            log.info("job=TrashCleanup done deleted_dirs={} cutoff={}", deleted, cutoff);
        }
        return Map.of("deleted_dirs", String.valueOf(deleted), "cutoff", cutoff.toString());
    }
}
