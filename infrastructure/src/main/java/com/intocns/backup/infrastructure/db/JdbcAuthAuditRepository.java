package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.AuthAuditLog;
import com.intocns.backup.domain.port.AuthAuditPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class JdbcAuthAuditRepository implements AuthAuditPort {

    private final JdbcClient jdbc;

    public JdbcAuthAuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuthAuditLog entry) {
        jdbc.sql("""
                INSERT INTO auth_audit_log (id, client_id, cocode, ip_address, result, created_at)
                VALUES (:id, :clientId, :cocode, :ipAddress, :result, :createdAt)
                """)
                .param("id", entry.id().toString())
                .param("clientId", entry.clientId())
                .param("cocode", entry.hospitalId() != null ? entry.hospitalId().cocode() : null)
                .param("ipAddress", entry.ipAddress())
                .param("result", entry.result().name())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
    }
}
