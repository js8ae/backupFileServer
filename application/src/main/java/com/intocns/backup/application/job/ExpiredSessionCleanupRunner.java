package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.AuditLogPort;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class ExpiredSessionCleanupRunner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionCleanupRunner.class);

    private final UploadSessionRepository sessionRepository;
    private final ChunkedUploadProtocol protocol;
    private final AuditLogPort auditLogPort;

    ExpiredSessionCleanupRunner(UploadSessionRepository sessionRepository,
                                ChunkedUploadProtocol protocol,
                                AuditLogPort auditLogPort) {
        this.sessionRepository = sessionRepository;
        this.protocol = protocol;
        this.auditLogPort = auditLogPort;
    }

    @Transactional
    public Map<String, String> execute(Instant now) {
        List<UploadSession> expired = sessionRepository.findExpiredBefore(now);
        if (expired.isEmpty()) {
            return Map.of("cleaned", "0", "failed", "0");
        }

        log.info("job=ExpiredSessionCleanup expired_count={}", expired.size());

        int cleaned = 0;
        int failed = 0;
        for (UploadSession session : expired) {
            try {
                if (session.tusUploadUri() != null) {
                    protocol.deleteUpload(session.tusUploadUri());
                }
                sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);
                auditLogPort.record(new AuditLog(
                        UUID.randomUUID(), session.id(), null, session.hospitalId(),
                        AuditEvent.UPLOAD_EXPIRED,
                        Map.of("filename", session.originalFilename(), "type", session.type().name()),
                        now
                ));
                cleaned++;
            } catch (IOException e) {
                log.error("job=ExpiredSessionCleanup session_id={} error=tus_delete_failed msg={}",
                        session.id(), e.getMessage());
                failed++;
            }
        }

        log.info("job=ExpiredSessionCleanup done cleaned={} failed={}", cleaned, failed);
        return Map.of("cleaned", String.valueOf(cleaned), "failed", String.valueOf(failed));
    }
}
