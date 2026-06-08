package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.CredentialInfo;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import com.intocns.backup.infrastructure.security.HospitalCredential;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcHospitalCredentialRepository implements HospitalCredentialRepository {

    private final JdbcClient jdbcClient;

    public JdbcHospitalCredentialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<HospitalCredential> findActiveByClientId(String clientId) {
        return jdbcClient.sql("""
                SELECT hc.cocode, hc.client_id, hc.client_secret_hash
                FROM hospital_credential hc
                JOIN hospital h ON h.cocode = hc.cocode
                WHERE hc.client_id = :clientId
                  AND hc.revoked_at IS NULL
                  AND h.is_active = TRUE
                """)
                .param("clientId", clientId)
                .query((rs, rowNum) -> new HospitalCredential(
                        new HospitalId(rs.getLong("cocode")),
                        rs.getString("client_id"),
                        rs.getString("client_secret_hash")
                ))
                .optional();
    }

    @Override
    public void save(HospitalId hospitalId, String clientId, String clientSecretHash, Instant createdAt) {
        jdbcClient.sql("""
                INSERT INTO hospital_credential (cocode, client_id, client_secret_hash, created_at)
                VALUES (:cocode, :clientId, :clientSecretHash, :createdAt)
                """)
                .param("cocode", hospitalId.cocode())
                .param("clientId", clientId)
                .param("clientSecretHash", clientSecretHash)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    @Override
    public List<CredentialInfo> findAllActiveByHospitalId(HospitalId hospitalId) {
        return jdbcClient.sql("""
                SELECT client_id, created_at
                FROM hospital_credential
                WHERE cocode = :cocode
                  AND revoked_at IS NULL
                ORDER BY created_at DESC
                """)
                .param("cocode", hospitalId.cocode())
                .query((rs, rowNum) -> new CredentialInfo(
                        rs.getString("client_id"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    @Override
    public boolean revoke(HospitalId hospitalId, String clientId, Instant revokedAt) {
        int updated = jdbcClient.sql("""
                UPDATE hospital_credential
                SET revoked_at = :revokedAt
                WHERE cocode = :cocode
                  AND client_id = :clientId
                  AND revoked_at IS NULL
                """)
                .param("revokedAt", Timestamp.from(revokedAt))
                .param("cocode", hospitalId.cocode())
                .param("clientId", clientId)
                .update();
        return updated > 0;
    }
}
