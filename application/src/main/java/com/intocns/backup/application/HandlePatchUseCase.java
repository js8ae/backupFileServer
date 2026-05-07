package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@Transactional
public class HandlePatchUseCase {

    private final UploadSessionRepository sessionRepository;
    private final ChunkedUploadProtocol protocol;
    private final FinalizeUploadUseCase finalizeUploadUseCase;

    public HandlePatchUseCase(
            UploadSessionRepository sessionRepository,
            ChunkedUploadProtocol protocol,
            FinalizeUploadUseCase finalizeUploadUseCase) {
        this.sessionRepository = sessionRepository;
        this.protocol = protocol;
        this.finalizeUploadUseCase = finalizeUploadUseCase;
    }

    @Transactional(readOnly = true)
    public void verifyAccess(String tusUploadUri, HospitalId caller) {
        UploadSession session = sessionRepository.findByTusUploadUri(tusUploadUri)
                .orElseThrow(() -> new SessionNotFoundException(null));
        if (!session.hospitalId().equals(caller)) {
            throw new UnauthorizedSessionAccessException(session.id(), caller);
        }
    }

    public void handle(String tusUploadUri, HospitalId caller) throws IOException {
        UploadSession session = sessionRepository.findByTusUploadUri(tusUploadUri)
                .orElseThrow(() -> new SessionNotFoundException(null));

        if (!session.hospitalId().equals(caller)) {
            throw new UnauthorizedSessionAccessException(session.id(), caller);
        }

        ChunkedUploadProtocol.Info info = protocol.getUploadInfo(tusUploadUri)
                .orElseThrow(() -> new SessionNotFoundException(null));

        if (info.completed()) {
            finalizeUploadUseCase.finalize(session.id());
        } else {
            sessionRepository.updateOffset(session.id(), info.offset());
        }
    }
}
