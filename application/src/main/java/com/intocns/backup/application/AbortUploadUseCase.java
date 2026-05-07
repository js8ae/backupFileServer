package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Service
@Transactional
public class AbortUploadUseCase {

    private final UploadSessionRepository sessionRepository;
    private final ChunkedUploadProtocol protocol;

    public AbortUploadUseCase(UploadSessionRepository sessionRepository, ChunkedUploadProtocol protocol) {
        this.sessionRepository = sessionRepository;
        this.protocol = protocol;
    }

    public void abort(UUID sessionId, HospitalId requestingHospital) throws IOException {
        UploadSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.hospitalId().equals(requestingHospital)) {
            throw new UnauthorizedSessionAccessException(sessionId, requestingHospital);
        }

        if (session.tusUploadUri() != null) {
            protocol.deleteUpload(session.tusUploadUri());
        }
        sessionRepository.updateStatus(sessionId, UploadStatus.ABORTED);
    }
}
