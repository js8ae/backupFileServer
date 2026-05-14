package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.JobExecutionLog;
import com.intocns.backup.domain.port.JobExecutionLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;

@Repository
public class JdbcJobExecutionLogRepository implements JobExecutionLogPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcJobExecutionLogRepository.class);

    private final JdbcClient jdbc;

    public JdbcJobExecutionLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(JobExecutionLog entry) {
        try {
            jdbc.sql("""
                    INSERT INTO job_execution_log
                        (id, job_name, started_at, finished_at, status, summary, error_msg)
                    VALUES
                        (:id, :jobName, :startedAt, :finishedAt, :status, :summary, :errorMsg)
                    """)
                    .param("id", entry.id().toString())
                    .param("jobName", entry.jobName())
                    .param("startedAt", Timestamp.from(entry.startedAt()))
                    .param("finishedAt", Timestamp.from(entry.finishedAt()))
                    .param("status", entry.status().name())
                    .param("summary", toJson(entry.summary()))
                    .param("errorMsg", entry.errorMessage())
                    .update();
        } catch (Exception e) {
            log.error("Failed to record job execution log job={} msg={}", entry.jobName(), e.getMessage());
        }
    }

    private static String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb
                .append("\"").append(k.replace("\"", "\\\"")).append("\":")
                .append("\"").append(v.replace("\"", "\\\"")).append("\","));
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }
}
