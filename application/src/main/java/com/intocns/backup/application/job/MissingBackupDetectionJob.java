package com.intocns.backup.application.job;

import com.intocns.backup.application.DetectMissingBackupsUseCase;
import com.intocns.backup.application.DetectMissingBackupsUseCase.MissingInfo;
import com.intocns.backup.domain.model.JobExecutionLog;
import com.intocns.backup.domain.model.JobExecutionStatus;
import com.intocns.backup.domain.port.JobExecutionLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MissingBackupDetectionJob {

    private static final Logger log = LoggerFactory.getLogger(MissingBackupDetectionJob.class);
    private static final String JOB_NAME = "MissingBackupDetectionJob";

    private final DetectMissingBackupsUseCase detectMissingBackups;
    private final JobExecutionLogPort jobExecutionLogPort;

    public MissingBackupDetectionJob(DetectMissingBackupsUseCase detectMissingBackups,
                                     JobExecutionLogPort jobExecutionLogPort) {
        this.detectMissingBackups = detectMissingBackups;
        this.jobExecutionLogPort = jobExecutionLogPort;
    }

    @Scheduled(cron = "0 0 7 * * *")  // 매일 07:00
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
        List<MissingInfo> missing = detectMissingBackups.detect();

        if (missing.isEmpty()) {
            log.info("job=MissingBackupDetection result=ALL_OK db_threshold={}h file_threshold={}h",
                    detectMissingBackups.dbMissingHours(), detectMissingBackups.fileMissingHours());
            return Map.of("missing_count", "0", "missing_hospitals", "");
        }

        List<String> missingDbCocode = missing.stream()
                .filter(MissingInfo::dbMissing)
                .map(m -> String.valueOf(m.hospital().id().cocode()))
                .collect(Collectors.toList());

        List<String> missingFileCocode = missing.stream()
                .filter(MissingInfo::fileMissing)
                .map(m -> String.valueOf(m.hospital().id().cocode()))
                .collect(Collectors.toList());

        for (MissingInfo info : missing) {
            log.warn("job=MissingBackupDetection MISSING cocode={} name={} db_missing={} db_last={} file_missing={} file_last={}",
                    info.hospital().id().cocode(),
                    info.hospital().name(),
                    info.dbMissing(),
                    info.dbLastBackupAt(),
                    info.fileMissing(),
                    info.fileLastBackupAt());
        }

        log.warn("job=MissingBackupDetection done missing_total={} missing_db={} missing_file={}",
                missing.size(), missingDbCocode.size(), missingFileCocode.size());

        return Map.of(
                "missing_count", String.valueOf(missing.size()),
                "missing_db_count", String.valueOf(missingDbCocode.size()),
                "missing_db_hospitals", String.join(",", missingDbCocode),
                "missing_file_count", String.valueOf(missingFileCocode.size()),
                "missing_file_hospitals", String.join(",", missingFileCocode)
        );
    }
}
