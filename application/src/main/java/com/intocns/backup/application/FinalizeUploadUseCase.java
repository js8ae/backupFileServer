package com.intocns.backup.application;

import com.intocns.backup.domain.exception.IntegrityCheckFailedException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.BackupArtifact;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;
import com.intocns.backup.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FinalizeUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizeUploadUseCase.class);
    private static final int DB_MAX_COUNT = 3;

    private final UploadSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;
    private final QuotaRepository quotaRepository;
    private final BackupStoragePort storage;
    private final ChunkedUploadProtocol protocol;

    public FinalizeUploadUseCase(
            UploadSessionRepository sessionRepository,
            ArtifactRepository artifactRepository,
            QuotaRepository quotaRepository,
            BackupStoragePort storage,
            ChunkedUploadProtocol protocol) {
        this.sessionRepository = sessionRepository;
        this.artifactRepository = artifactRepository;
        this.quotaRepository = quotaRepository;
        this.storage = storage;
        this.protocol = protocol;
    }

    public void finalize(UUID sessionId) throws IOException {
        UploadSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        Path incomingData = protocol.resolveDataPath(session.tusUploadUri());

        String actualSha256 = storage.sha256(incomingData);
        if (session.expectedSha256() != null && !session.expectedSha256().equalsIgnoreCase(actualSha256)) {
            throw new IntegrityCheckFailedException(sessionId, session.expectedSha256(), actualSha256);
        }

        Instant now = Instant.now();

        // DB: 최대 3개 유지 — 초과 시 오래된 것부터 삭제
        if (session.type() == BackupType.DB) {
            evictOldestDbArtifacts(session.hospitalId(), now);
        }

        Path artifactPath = storage.promoteToArtifacts(
            incomingData, session.hospitalId(), session.type(), session.originalFilename());

        BackupArtifact artifact = new BackupArtifact(
            UUID.randomUUID(),
            session.hospitalId(),
            session.type(),
            artifactPath.toString(),
            session.totalSize(),
            actualSha256,
            now,
            null,   // 무기한 보관
            null
        );
        artifactRepository.save(artifact);
        quotaRepository.addUsage(session.hospitalId(), session.totalSize());
        sessionRepository.updateOffset(sessionId, session.totalSize());
        sessionRepository.updateStatus(sessionId, UploadStatus.COMPLETED);
        protocol.deleteUpload(session.tusUploadUri());
    }

    private void evictOldestDbArtifacts(com.intocns.backup.domain.model.HospitalId hospitalId, Instant now) throws IOException {
        List<BackupArtifact> existing = artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.DB);
        // oldest-first 순서로 반환되므로, count >= DB_MAX_COUNT 이면 앞에서부터 제거
        int toEvict = existing.size() - (DB_MAX_COUNT - 1);
        for (int i = 0; i < toEvict; i++) {
            BackupArtifact oldest = existing.get(i);
            storage.moveToTrash(Path.of(oldest.storagePath()));
            quotaRepository.subtractUsage(oldest.hospitalId(), oldest.sizeBytes());
            artifactRepository.markPurged(oldest.id(), now);
            log.info("evict=DB artifact_id={} cocode={}", oldest.id(), hospitalId.cocode());
        }
    }
}
