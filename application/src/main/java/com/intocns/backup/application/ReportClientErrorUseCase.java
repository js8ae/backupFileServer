package com.intocns.backup.application;

import com.intocns.backup.domain.model.ClientUploadError;
import com.intocns.backup.domain.model.HospitalId;
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
                       String uploadUri,
                       HospitalId hospitalIdFromJwt,
                       String errorType,
                       String errorMessage,
                       Long byteOffset,
                       Map<String, String> clientInfo,
                       Instant occurredAt) {
        // 클라이언트는 보통 upload_session UUID 대신 TUS URI(/files/{tusId})만 보관하므로
        // sessionId 또는 uploadUri 중 가능한 것으로 세션을 복원해 session_id·cocode 를 채운다.
        UploadSession session = resolveSession(sessionId, uploadUri);

        UUID resolvedSessionId = session != null ? session.id() : sessionId;
        // cocode 우선순위: JWT → 세션 복원 → client_info.cocode (클라이언트가 항상 넣어줌)
        HospitalId resolvedHospitalId = hospitalIdFromJwt != null
                ? hospitalIdFromJwt
                : session != null ? session.hospitalId()
                : cocodeFromClientInfo(clientInfo);

        ClientUploadError error = new ClientUploadError(
                UUID.randomUUID(),
                resolvedSessionId,
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

    private UploadSession resolveSession(UUID sessionId, String uploadUri) {
        if (sessionId != null) {
            UploadSession byId = sessionRepository.findById(sessionId).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        if (uploadUri != null && !uploadUri.isBlank()) {
            return sessionRepository.findByTusUploadUri(uploadUri).orElse(null);
        }
        return null;
    }

    private HospitalId cocodeFromClientInfo(Map<String, String> clientInfo) {
        if (clientInfo == null) {
            return null;
        }
        String raw = clientInfo.get("cocode");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new HospitalId(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
