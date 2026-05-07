package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class LinkTusUploadUseCase {

    private final UploadSessionRepository sessionRepository;

    public LinkTusUploadUseCase(UploadSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void link(UUID sessionId, String tusUploadUri, HospitalId caller) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.hospitalId().equals(caller)) {
            throw new UnauthorizedSessionAccessException(sessionId, caller);
        }

        sessionRepository.updateTusUploadUri(sessionId, tusUploadUri);
    }
}
