package com.intocns.backup.application;

import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ResetHospitalDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetHospitalDataUseCase.class);

    private final ArtifactRepository artifactRepository;
    private final UploadSessionRepository sessionRepository;
    private final QuotaRepository quotaRepository;
    private final BackupStoragePort storagePort;
    private final ChunkedUploadProtocol protocol;
    private final AuditLogPort auditLogPort;

    public ResetHospitalDataUseCase(ArtifactRepository artifactRepository,
                                    UploadSessionRepository sessionRepository,
                                    QuotaRepository quotaRepository,
                                    BackupStoragePort storagePort,
                                    ChunkedUploadProtocol protocol,
                                    AuditLogPort auditLogPort) {
        this.artifactRepository = artifactRepository;
        this.sessionRepository = sessionRepository;
        this.quotaRepository = quotaRepository;
        this.storagePort = storagePort;
        this.protocol = protocol;
        this.auditLogPort = auditLogPort;
    }

    public record Result(int artifactsMoved, int sessionsAborted) {}

    @Transactional
    public Result reset(HospitalId hospitalId) {
        Instant now = Instant.now();

        // 1. 활성 artifact 전체 (DB + FILE) → trash 이동
        List<BackupArtifact> allActive = new ArrayList<>();
        allActive.addAll(artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.DB));
        allActive.addAll(artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.FILE));

        int artifactsMoved = 0;
        for (BackupArtifact artifact : allActive) {
            try {
                storagePort.moveToTrash(Path.of(artifact.storagePath()));
            } catch (IOException e) {
                log.error("reset moveToTrash failed cocode={} artifact_id={} msg={}",
                        hospitalId.cocode(), artifact.id(), e.getMessage());
            }
            if (artifactRepository.markPurged(artifact.id(), now)) {
                artifactsMoved++;
                auditLogPort.record(new AuditLog(
                        UUID.randomUUID(), null, artifact.id(), hospitalId,
                        AuditEvent.ARTIFACT_EVICTED,
                        Map.of("type", artifact.type().name(),
                               "size_bytes", String.valueOf(artifact.sizeBytes()),
                               "reason", "HOSPITAL_RESET"),
                        now
                ));
            }
        }

        // 2. 진행 중인 세션 → TUS 임시 파일 삭제 + ABORTED
        List<UploadSession> sessions = sessionRepository.findByHospitalId(hospitalId);
        int sessionsAborted = 0;
        for (UploadSession session : sessions) {
            if (session.status() != UploadStatus.INITIATED && session.status() != UploadStatus.UPLOADING) {
                continue;
            }
            if (session.tusUploadUri() != null) {
                try {
                    protocol.deleteUpload(session.tusUploadUri());
                } catch (IOException e) {
                    log.warn("reset tus delete failed cocode={} session_id={} msg={}",
                            hospitalId.cocode(), session.id(), e.getMessage());
                }
            }
            sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);
            sessionsAborted++;
        }

        // 3. 쿼터 초기화
        quotaRepository.resetUsage(hospitalId);

        log.info("reset cocode={} artifacts_moved={} sessions_aborted={}",
                hospitalId.cocode(), artifactsMoved, sessionsAborted);

        auditLogPort.record(new AuditLog(
                UUID.randomUUID(), null, null, hospitalId,
                AuditEvent.HOSPITAL_RESET,
                Map.of("artifacts_moved", String.valueOf(artifactsMoved),
                       "sessions_aborted", String.valueOf(sessionsAborted)),
                now
        ));

        return new Result(artifactsMoved, sessionsAborted);
    }
}
