package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.infrastructure.security.HospitalCredential;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcHospitalCredentialRepository {

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
                  AND hc.active = TRUE
                  AND h.active = TRUE
                """)
                .param("clientId", clientId)
                .query((rs, _) -> new HospitalCredential(
                        new HospitalId(rs.getLong("cocode")),
                        rs.getString("client_id"),
                        rs.getString("client_secret_hash")
                ))
                .optional();
    }
}
