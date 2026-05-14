package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.JobExecutionLog;
import com.intocns.backup.domain.model.JobExecutionStatus;
import com.intocns.backup.domain.port.JobExecutionLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RetentionPolicyJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyJob.class);
    private static final String JOB_NAME = "RetentionPolicyJob";

    private final RetentionPolicyRunner runner;
    private final JobExecutionLogPort jobExecutionLogPort;

    public RetentionPolicyJob(RetentionPolicyRunner runner,
                              JobExecutionLogPort jobExecutionLogPort) {
        this.runner = runner;
        this.jobExecutionLogPort = jobExecutionLogPort;
    }

    @Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
    public void run() {
        Instant startedAt = Instant.now();
        try {
            var summary = runner.execute(startedAt);
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
}
