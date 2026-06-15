package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.ClientUploadError;
import com.intocns.backup.domain.port.ClientUploadErrorLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;

@Repository
public class JdbcClientUploadErrorLogRepository implements ClientUploadErrorLogPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcClientUploadErrorLogRepository.class);

    private final JdbcClient jdbc;

    public JdbcClientUploadErrorLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(ClientUploadError error) {
        try {
            jdbc.sql("""
                    INSERT INTO client_upload_error_log
                        (id, session_id, cocode, error_type, error_message, byte_offset, client_info, occurred_at, reported_at)
                    VALUES
                        (:id, :sessionId, :cocode, :errorType, :errorMessage, :byteOffset, :clientInfo, :occurredAt, :reportedAt)
                    """)
                    .param("id", error.id().toString())
                    .param("sessionId", error.sessionId().toString())
                    .param("cocode", error.hospitalId().cocode())
                    .param("errorType", error.errorType())
                    .param("errorMessage", error.errorMessage())
                    .param("byteOffset", error.byteOffset())
                    .param("clientInfo", toJson(error.clientInfo()))
                    .param("occurredAt", Timestamp.from(error.occurredAt()))
                    .param("reportedAt", Timestamp.from(error.reportedAt()))
                    .update();
        } catch (Exception e) {
            log.error("Failed to record client upload error session={} msg={}", error.sessionId(), e.getMessage());
        }
    }

    private static String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb
                .append("\"").append(k.replace("\"", "\\\"")).append("\":")
                .append("\"").append(v == null ? "" : v.replace("\"", "\\\"")).append("\","));
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }
}
