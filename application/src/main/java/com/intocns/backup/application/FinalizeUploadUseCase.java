package com.intocns.backup.application;

import com.intocns.backup.domain.exception.IntegrityCheckFailedException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;
import com.intocns.backup.domain.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
public class FinalizeUploadUseCase {

    private final UploadSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;
    private final QuotaRepository quotaRepository;
    private final BackupStoragePort storage;
    private final ChunkedUploadProtocol protocol;
    private final long retentionDbDays;
    private final long retentionFileDays;

    public FinalizeUploadUseCase(
            UploadSessionRepository sessionRepository,
            ArtifactRepository artifactRepository,
            QuotaRepository quotaRepository,
            BackupStoragePort storage,
            ChunkedUploadProtocol protocol,
            @Value("${backup.retention.db-days:30}") long retentionDbDays,
            @Value("${backup.retention.file-days:90}") long retentionFileDays) {
        this.sessionRepository = sessionRepository;
        this.artifactRepository = artifactRepository;
        this.quotaRepository = quotaRepository;
        this.storage = storage;
        this.protocol = protocol;
        this.retentionDbDays = retentionDbDays;
        this.retentionFileDays = retentionFileDays;
    }

    public void finalize(UUID sessionId) throws IOException {
        UploadSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        Path incomingData = protocol.resolveDataPath(session.tusUploadUri());

        String actualSha256 = storage.sha256(incomingData);
        if (session.expectedSha256() != null && !session.expectedSha256().equalsIgnoreCase(actualSha256)) {
            throw new IntegrityCheckFailedException(sessionId, session.expectedSha256(), actualSha256);
        }

        long retentionDays = switch (session.type()) {
            case DB -> retentionDbDays;
            case FILE -> retentionFileDays;
        };

        Path artifactPath = storage.promoteToArtifacts(
            incomingData, session.hospitalId(), session.type(), session.originalFilename());

        BackupArtifact artifact = new BackupArtifact(
            UUID.randomUUID(),
            session.hospitalId(),
            session.type(),
            artifactPath.toString(),
            session.totalSize(),
            actualSha256,
            Instant.now(),
            Instant.now().plus(retentionDays, ChronoUnit.DAYS),
            null
        );
        artifactRepository.save(artifact);
        quotaRepository.addUsage(session.hospitalId(), session.totalSize());
        sessionRepository.updateStatus(sessionId, UploadStatus.COMPLETED);
        protocol.deleteUpload(session.tusUploadUri());
    }
}
