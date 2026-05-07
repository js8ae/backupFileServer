package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.HospitalQuota;
import com.intocns.backup.domain.port.QuotaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcQuotaRepository implements QuotaRepository {

    private final JdbcClient jdbc;

    public JdbcQuotaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<HospitalQuota> findByHospitalId(HospitalId hospitalId) {
        return jdbc.sql("SELECT * FROM hospital_quota WHERE cocode = :cocode")
                .param("cocode", hospitalId.cocode())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void initializeQuota(HospitalId hospitalId, long limitBytes) {
        jdbc.sql("""
                INSERT INTO hospital_quota (cocode, used_bytes, limit_bytes, last_calculated_at)
                VALUES (:cocode, 0, :limitBytes, :now)
                """)
                .param("cocode", hospitalId.cocode())
                .param("limitBytes", limitBytes)
                .param("now", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void updateLimit(HospitalId hospitalId, long limitBytes) {
        jdbc.sql("""
                UPDATE hospital_quota
                SET limit_bytes = :limitBytes, last_calculated_at = :now
                WHERE cocode = :cocode
                """)
                .param("limitBytes", limitBytes)
                .param("now", Timestamp.from(Instant.now()))
                .param("cocode", hospitalId.cocode())
                .update();
    }

    @Override
    public void addUsage(HospitalId hospitalId, long bytes) {
        jdbc.sql("""
                INSERT INTO hospital_quota (cocode, used_bytes, limit_bytes, last_calculated_at)
                VALUES (:cocode, :bytes, 9223372036854775807, :now)
                ON DUPLICATE KEY UPDATE
                    used_bytes         = used_bytes + :bytes,
                    last_calculated_at = :now
                """)
                .param("cocode", hospitalId.cocode())
                .param("bytes", bytes)
                .param("now", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void subtractUsage(HospitalId hospitalId, long bytes) {
        jdbc.sql("""
                UPDATE hospital_quota
                SET used_bytes         = GREATEST(0, used_bytes - :bytes),
                    last_calculated_at = :now
                WHERE cocode = :cocode
                """)
                .param("bytes", bytes)
                .param("now", Timestamp.from(Instant.now()))
                .param("cocode", hospitalId.cocode())
                .update();
    }

    private HospitalQuota mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new HospitalQuota(
                new HospitalId(rs.getLong("cocode")),
                rs.getLong("used_bytes"),
                rs.getLong("limit_bytes")
        );
    }
}
