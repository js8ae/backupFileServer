package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.AuditLogPort;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AbortUploadUseCase {

    private final UploadSessionRepository sessionRepository;
    private final ChunkedUploadProtocol protocol;
    private final AuditLogPort auditLogPort;

    public AbortUploadUseCase(UploadSessionRepository sessionRepository, ChunkedUploadProtocol protocol,
                              AuditLogPort auditLogPort) {
        this.sessionRepository = sessionRepository;
        this.protocol = protocol;
        this.auditLogPort = auditLogPort;
    }

    public void abort(UUID sessionId, HospitalId requestingHospital) throws IOException {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        doAbort(session, requestingHospital);
    }

    public void abortByTusUri(String tusUploadUri, HospitalId requestingHospital) throws IOException {
        UploadSession session = sessionRepository.findByTusUploadUri(tusUploadUri)
                .orElseThrow(() -> new SessionNotFoundException(null));
        doAbort(session, requestingHospital);
    }

    public void forceAbort(UUID sessionId) throws IOException {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.tusUploadUri() != null) {
            protocol.deleteUpload(session.tusUploadUri());
        }
        sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);
    }

    private void doAbort(UploadSession session, HospitalId requestingHospital) throws IOException {
        if (!session.hospitalId().equals(requestingHospital)) {
            throw new UnauthorizedSessionAccessException(session.id(), requestingHospital);
        }
        if (session.tusUploadUri() != null) {
            protocol.deleteUpload(session.tusUploadUri());
        }
        sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);
        auditLogPort.record(new AuditLog(
            UUID.randomUUID(), session.id(), null, session.hospitalId(),
            AuditEvent.UPLOAD_ABORTED,
            Map.of("filename", session.originalFilename(), "type", session.type().name()),
            Instant.now()
        ));
    }
}
