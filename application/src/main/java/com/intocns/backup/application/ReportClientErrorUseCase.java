package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.ClientUploadError;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.port.ClientUploadErrorLogPort;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportClientErrorUseCase {

    private final UploadSessionRepository sessionRepository;
    private final ClientUploadErrorLogPort errorLogPort;

    public ReportClientErrorUseCase(UploadSessionRepository sessionRepository,
                                    ClientUploadErrorLogPort errorLogPort) {
        this.sessionRepository = sessionRepository;
        this.errorLogPort = errorLogPort;
    }

    public void report(UUID sessionId,
                       String errorType,
                       String errorMessage,
                       Long byteOffset,
                       Map<String, String> clientInfo,
                       Instant occurredAt) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        ClientUploadError error = new ClientUploadError(
                UUID.randomUUID(),
                sessionId,
                session.hospitalId(),
                errorType,
                errorMessage,
                byteOffset,
                clientInfo,
                occurredAt,
                Instant.now()
        );

        errorLogPort.record(error);
    }
}
