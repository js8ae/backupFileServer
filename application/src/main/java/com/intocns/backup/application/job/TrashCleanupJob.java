package com.intocns.backup.application.job;

import com.intocns.backup.domain.port.BackupStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class TrashCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TrashCleanupJob.class);

    private final BackupStoragePort storagePort;
    private final int trashDays;

    public TrashCleanupJob(BackupStoragePort storagePort,
                           @Value("${backup.retention.trash-days:7}") int trashDays) {
        this.storagePort = storagePort;
        this.trashDays = trashDays;
    }

    @Scheduled(cron = "0 0 4 * * *")  // 매일 새벽 4시
    public void run() {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(trashDays);
        try {
            int deleted = storagePort.purgeTrash(cutoff);
            if (deleted > 0) {
                log.info("job=TrashCleanup done deleted_dirs={} cutoff={}", deleted, cutoff);
            }
        } catch (IOException e) {
            log.error("job=TrashCleanup failed msg={}", e.getMessage(), e);
        }
    }
}
