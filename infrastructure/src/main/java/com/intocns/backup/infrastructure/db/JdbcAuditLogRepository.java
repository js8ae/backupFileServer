package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.AuditLog;
import com.intocns.backup.domain.port.AuditLogPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;

@Repository
public class JdbcAuditLogRepository implements AuditLogPort {

    private final JdbcClient jdbc;

    public JdbcAuditLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditLog entry) {
        jdbc.sql("""
                INSERT INTO backup_audit_log (id, session_id, artifact_id, cocode, event, detail, created_at)
                VALUES (:id, :sessionId, :artifactId, :cocode, :event, :detail, :createdAt)
                """)
                .param("id", entry.id().toString())
                .param("sessionId", entry.sessionId() != null ? entry.sessionId().toString() : null)
                .param("artifactId", entry.artifactId() != null ? entry.artifactId().toString() : null)
                .param("cocode", entry.hospitalId().cocode())
                .param("event", entry.event().name())
                .param("detail", toJson(entry.detail()))
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
    }

    private static String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb
                .append("\"").append(k.replace("\"", "\\\"")).append("\":")
                .append("\"").append(v.replace("\"", "\\\"")).append("\","));
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }
}
