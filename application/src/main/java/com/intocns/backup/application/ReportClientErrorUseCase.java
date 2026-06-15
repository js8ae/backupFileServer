package com.intocns.backup.application;

import com.intocns.backup.domain.model.ClientUploadError;
import com.intocns.backup.domain.model.HospitalId;
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
                       HospitalId hospitalIdFromJwt,
                       String errorType,
                       String errorMessage,
                       Long byteOffset,
                       Map<String, String> clientInfo,
                       Instant occurredAt) {
        HospitalId resolvedHospitalId = resolveHospitalId(hospitalIdFromJwt, sessionId);

        ClientUploadError error = new ClientUploadError(
                UUID.randomUUID(),
                sessionId,
                resolvedHospitalId,
                errorType,
                errorMessage,
                byteOffset,
                clientInfo,
                occurredAt,
                Instant.now()
        );

        errorLogPort.record(error);
    }

    private HospitalId resolveHospitalId(HospitalId hospitalIdFromJwt, UUID sessionId) {
        if (hospitalIdFromJwt != null) {
            return hospitalIdFromJwt;
        }
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .map(s -> s.hospitalId())
                    .orElse(null);
        }
        return null;
    }
}
