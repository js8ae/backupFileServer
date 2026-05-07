package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

@Repository
public class JdbcUploadSessionRepository implements UploadSessionRepository {

    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private final JdbcClient jdbc;

    public JdbcUploadSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UploadSession save(UploadSession session) {
        jdbc.sql("""
                INSERT INTO upload_session
                    (id, cocode, type, original_filename, total_size,
                     current_offset, expected_sha256, tus_upload_uri, status, expires_at, created_at)
                VALUES
                    (:id, :cocode, :type, :originalFilename, :totalSize,
                     :currentOffset, :expectedSha256, :tusUploadUri, :status, :expiresAt, :createdAt)
                """)
                .param("id", session.id().toString())
                .param("cocode", session.hospitalId().cocode())
                .param("type", session.type().name())
                .param("originalFilename", session.originalFilename())
                .param("totalSize", session.totalSize())
                .param("currentOffset", session.currentOffset())
                .param("expectedSha256", session.expectedSha256())
                .param("tusUploadUri", session.tusUploadUri())
                .param("status", session.status().name())
                .param("expiresAt", Timestamp.from(session.expiresAt()))
                .param("createdAt", Timestamp.from(session.createdAt()))
                .update();
        return session;
    }

    @Override
    public Optional<UploadSession> findById(UUID id) {
        return jdbc.sql("SELECT * FROM upload_session WHERE id = :id")
                .param("id", id.toString())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<UploadSession> findByTusUploadUri(String tusUploadUri) {
        return jdbc.sql("SELECT * FROM upload_session WHERE tus_upload_uri = :uri")
                .param("uri", tusUploadUri)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void updateTusUploadUri(UUID id, String tusUploadUri) {
        jdbc.sql("UPDATE upload_session SET tus_upload_uri = :uri WHERE id = :id")
                .param("uri", tusUploadUri)
                .param("id", id.toString())
                .update();
    }

    @Override
    public void updateOffset(UUID id, long offset) {
        jdbc.sql("""
                UPDATE upload_session
                SET current_offset = :offset, status = 'UPLOADING'
                WHERE id = :id
                """)
                .param("offset", offset)
                .param("id", id.toString())
                .update();
    }

    @Override
    public void updateStatus(UUID id, UploadStatus status) {
        jdbc.sql("UPDATE upload_session SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", id.toString())
                .update();
    }

    @Override
    public List<UploadSession> findByHospitalId(HospitalId hospitalId) {
        return jdbc.sql("""
                SELECT * FROM upload_session
                WHERE cocode = :cocode
                ORDER BY created_at DESC
                """)
                .param("cocode", hospitalId.cocode())
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<UploadSession> findExpiredBefore(Instant threshold) {
        return jdbc.sql("""
                SELECT * FROM upload_session
                WHERE status IN ('INITIATED', 'UPLOADING')
                  AND expires_at < :threshold
                """)
                .param("threshold", Timestamp.from(threshold))
                .query(this::mapRow)
                .list();
    }

    private UploadSession mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UploadSession(
                UUID.fromString(rs.getString("id")),
                new HospitalId(rs.getLong("cocode")),
                BackupType.valueOf(rs.getString("type")),
                rs.getString("original_filename"),
                rs.getLong("total_size"),
                rs.getLong("current_offset"),
                rs.getString("expected_sha256"),
                rs.getString("tus_upload_uri"),
                UploadStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("expires_at", UTC).toInstant(),
                rs.getTimestamp("created_at", UTC).toInstant()
        );
    }
}
