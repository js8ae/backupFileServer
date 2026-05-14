package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.JobExecutionLog;

public interface JobExecutionLogPort {
    void record(JobExecutionLog log);
}
