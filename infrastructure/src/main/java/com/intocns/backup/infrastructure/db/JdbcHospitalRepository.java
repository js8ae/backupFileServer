package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Repository
public class JdbcHospitalRepository implements HospitalRepository {

    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private final JdbcClient jdbc;

    public JdbcHospitalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Hospital> findById(HospitalId id) {
        return jdbc.sql("SELECT * FROM hospital WHERE cocode = :cocode")
                .param("cocode", id.cocode())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<Hospital> findAll() {
        return jdbc.sql("SELECT * FROM hospital ORDER BY cocode")
                .query(this::mapRow)
                .list();
    }

    @Override
    public Hospital save(Hospital hospital) {
        jdbc.sql("""
                INSERT INTO hospital
                    (cocode, name, license_start_at, license_end_at, max_storage_bytes, is_active, created_at, updated_at)
                VALUES
                    (:cocode, :name, :licenseStartAt, :licenseEndAt, :maxStorageBytes, :isActive, :createdAt, :updatedAt)
                ON DUPLICATE KEY UPDATE
                    name              = :name,
                    license_start_at  = :licenseStartAt,
                    license_end_at    = :licenseEndAt,
                    max_storage_bytes = :maxStorageBytes,
                    is_active         = :isActive,
                    updated_at        = :updatedAt
                """)
                .param("cocode", hospital.id().cocode())
                .param("name", hospital.name())
                .param("licenseStartAt", Timestamp.from(hospital.licenseStartAt()))
                .param("licenseEndAt", Timestamp.from(hospital.licenseEndAt()))
                .param("maxStorageBytes", hospital.maxStorageBytes())
                .param("isActive", hospital.active())
                .param("createdAt", Timestamp.from(hospital.createdAt()))
                .param("updatedAt", Timestamp.from(hospital.updatedAt()))
                .update();
        return hospital;
    }

    private Hospital mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Hospital(
                new HospitalId(rs.getLong("cocode")),
                rs.getString("name"),
                rs.getTimestamp("license_start_at", UTC).toInstant(),
                rs.getTimestamp("license_end_at", UTC).toInstant(),
                rs.getLong("max_storage_bytes"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at", UTC).toInstant(),
                rs.getTimestamp("updated_at", UTC).toInstant()
        );
    }
}
