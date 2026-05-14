package com.intocns.backup.application;

import com.intocns.backup.domain.exception.LicenseExpiredException;
import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class InitiateUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateUploadUseCase.class);

    private final HospitalRepository hospitalRepository;
    private final UploadSessionRepository sessionRepository;
    private final QuotaRepository quotaRepository;
    private final ArtifactRepository artifactRepository;
    private final BackupStoragePort storagePort;
    private final AuditLogPort auditLogPort;
    private final long sessionTtlHours;

    public InitiateUploadUseCase(
            HospitalRepository hospitalRepository,
            UploadSessionRepository sessionRepository,
            QuotaRepository quotaRepository,
            ArtifactRepository artifactRepository,
            BackupStoragePort storagePort,
            AuditLogPort auditLogPort,
            @Value("${backup.session.ttl-hours:24}") long sessionTtlHours) {
        this.hospitalRepository = hospitalRepository;
        this.sessionRepository = sessionRepository;
        this.quotaRepository = quotaRepository;
        this.artifactRepository = artifactRepository;
        this.storagePort = storagePort;
        this.auditLogPort = auditLogPort;
        this.sessionTtlHours = sessionTtlHours;
    }

    public record Command(
        HospitalId hospitalId,
        BackupType type,
        String originalFilename,
        long totalSize,
        String expectedSha256
    ) {}

    public UUID initiate(Command command) throws IOException {
        Instant now = Instant.now();

        Hospital hospital = hospitalRepository.findById(command.hospitalId())
                .orElseThrow(() -> new SessionNotFoundException(null));

        if (!hospital.isLicenseValid(now)) {
            throw new LicenseExpiredException(command.hospitalId());
        }

        // FILE: DB 용량은 항상 보호, 초과분은 FILE 오래된 순 삭제
        // DB: 최대 3개 강제는 FinalizeUploadUseCase에서 처리 (항상 업로드 허용)
        if (command.type() == BackupType.FILE) {
            checkAndEvictForFileUpload(command.hospitalId(), command.totalSize(), now);
        }

        UploadSession session = new UploadSession(
            UUID.randomUUID(),
            command.hospitalId(),
            command.type(),
            command.originalFilename(),
            command.totalSize(),
            0L,
            command.expectedSha256(),
            null,
            UploadStatus.INITIATED,
            now.plus(sessionTtlHours, ChronoUnit.HOURS),
            now
        );
        sessionRepository.save(session);

        auditLogPort.record(new AuditLog(
            UUID.randomUUID(), session.id(), null, command.hospitalId(),
            AuditEvent.UPLOAD_INITIATED,
            Map.of("filename", command.originalFilename(),
                   "type", command.type().name(),
                   "total_size_bytes", String.valueOf(command.totalSize())),
            now
        ));

        return session.id();
    }

    private void checkAndEvictForFileUpload(HospitalId hospitalId, long newFileSize, Instant now) throws IOException {
        HospitalQuota quota = quotaRepository.findByHospitalId(hospitalId).orElse(null);
        if (quota == null) {
            return;
        }

        // DB artifacts는 항상 보호 — 먼저 예약 공간으로 확보
        List<BackupArtifact> dbArtifacts = artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.DB);
        long dbReserved = dbArtifacts.stream().mapToLong(BackupArtifact::sizeBytes).sum();

        long fileLimit = quota.limitBytes() - dbReserved;
        if (fileLimit <= 0) {
            // DB 파일만으로도 한도 초과 — FILE 업로드 불가
            throw new QuotaExceededException(hospitalId, quota.usedBytes(), quota.limitBytes());
        }

        List<BackupArtifact> fileArtifacts = artifactRepository.findByHospitalIdAndType(hospitalId, BackupType.FILE);
        long currentFileUsed = fileArtifacts.stream().mapToLong(BackupArtifact::sizeBytes).sum();

        if (currentFileUsed + newFileSize <= fileLimit) {
            return;
        }

        // 공간 확보를 위해 오래된 FILE artifact부터 삭제
        long needed = (currentFileUsed + newFileSize) - fileLimit;
        long freed = 0L;
        for (BackupArtifact artifact : fileArtifacts) { // oldest-first
            if (freed >= needed) break;
            storagePort.moveToTrash(Path.of(artifact.storagePath()));
            quotaRepository.subtractUsage(hospitalId, artifact.sizeBytes());
            artifactRepository.markPurged(artifact.id(), now);
            freed += artifact.sizeBytes();
            log.info("evict=FILE artifact_id={} cocode={} size_bytes={}", artifact.id(), hospitalId.cocode(), artifact.sizeBytes());
            auditLogPort.record(new AuditLog(
                UUID.randomUUID(), null, artifact.id(), hospitalId,
                AuditEvent.ARTIFACT_EVICTED,
                Map.of("type", BackupType.FILE.name(),
                       "size_bytes", String.valueOf(artifact.sizeBytes()),
                       "reason", "QUOTA_EXCEEDED"),
                now
            ));
        }

        if (currentFileUsed - freed + newFileSize > fileLimit) {
            throw new QuotaExceededException(hospitalId, quota.usedBytes(), quota.limitBytes());
        }
    }
}
