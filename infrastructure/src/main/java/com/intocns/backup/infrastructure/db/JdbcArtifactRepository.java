package com.intocns.backup.infrastructure.db;

import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.ArtifactRepository;
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
public class JdbcArtifactRepository implements ArtifactRepository {

    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private final JdbcClient jdbc;

    public JdbcArtifactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BackupArtifact save(BackupArtifact artifact) {
        jdbc.sql("""
                INSERT INTO backup_artifact
                    (id, cocode, type, storage_path, size_bytes, sha256, created_at, expires_at, purged_at)
                VALUES
                    (:id, :cocode, :type, :storagePath, :sizeBytes, :sha256, :createdAt, :expiresAt, :purgedAt)
                """)
                .param("id", artifact.id().toString())
                .param("cocode", artifact.hospitalId().cocode())
                .param("type", artifact.type().name())
                .param("storagePath", artifact.storagePath())
                .param("sizeBytes", artifact.sizeBytes())
                .param("sha256", artifact.sha256())
                .param("createdAt", Timestamp.from(artifact.createdAt()))
                .param("expiresAt", Timestamp.from(artifact.expiresAt()))
                .param("purgedAt", artifact.purgedAt() != null ? Timestamp.from(artifact.purgedAt()) : null)
                .update();
        return artifact;
    }

    @Override
    public Optional<BackupArtifact> findById(UUID id) {
        return jdbc.sql("SELECT * FROM backup_artifact WHERE id = :id")
                .param("id", id.toString())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<BackupArtifact> findExpiredNotPurgedBefore(Instant threshold) {
        return jdbc.sql("""
                SELECT * FROM backup_artifact
                WHERE purged_at IS NULL
                  AND expires_at < :threshold
                """)
                .param("threshold", Timestamp.from(threshold))
                .query(this::mapRow)
                .list();
    }

    @Override
    public void markPurged(UUID id, Instant purgedAt) {
        jdbc.sql("UPDATE backup_artifact SET purged_at = :purgedAt WHERE id = :id")
                .param("purgedAt", Timestamp.from(purgedAt))
                .param("id", id.toString())
                .update();
    }

    private BackupArtifact mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp purgedAt = rs.getTimestamp("purged_at", UTC);
        return new BackupArtifact(
                UUID.fromString(rs.getString("id")),
                new HospitalId(rs.getLong("cocode")),
                BackupType.valueOf(rs.getString("type")),
                rs.getString("storage_path"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getTimestamp("created_at", UTC).toInstant(),
                rs.getTimestamp("expires_at", UTC).toInstant(),
                purgedAt != null ? purgedAt.toInstant() : null
        );
    }
}
