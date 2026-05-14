package com.intocns.backup.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobExecutionLog(
        UUID id,
        String jobName,
        Instant startedAt,
        Instant finishedAt,
        JobExecutionStatus status,
        Map<String, String> summary,
        String errorMessage
) {}
