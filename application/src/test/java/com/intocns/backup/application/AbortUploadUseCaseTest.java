package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbortUploadUseCaseTest {

    @Mock UploadSessionRepository sessionRepository;
    @Mock ChunkedUploadProtocol protocol;

    AbortUploadUseCase useCase;

    static final HospitalId OWNER = new HospitalId(1001L);
    static final HospitalId OTHER = new HospitalId(9999L);
    static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AbortUploadUseCase(sessionRepository, protocol);
    }

    @Test
    void 정상_중단_TUS_URI_있을때() throws IOException {
        UploadSession session = session("/files/tus-id");
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        useCase.abort(SESSION_ID, OWNER);

        verify(protocol).deleteUpload("/files/tus-id");
        verify(sessionRepository).updateStatus(SESSION_ID, UploadStatus.ABORTED);
    }

    @Test
    void TUS_URI_없으면_삭제_호출_생략() throws IOException {
        UploadSession session = session(null);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        useCase.abort(SESSION_ID, OWNER);

        verify(protocol, never()).deleteUpload(any());
        verify(sessionRepository).updateStatus(SESSION_ID, UploadStatus.ABORTED);
    }

    @Test
    void 다른_병원_접근시_UnauthorizedSessionAccessException() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session("/files/tus-id")));

        assertThrows(UnauthorizedSessionAccessException.class, () -> useCase.abort(SESSION_ID, OTHER));
        verifyNoInteractions(protocol);
    }

    @Test
    void forceAbort는_소유권_검사_없이_중단() throws IOException {
        UploadSession session = session("/files/tus-id");
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        useCase.forceAbort(SESSION_ID);

        verify(protocol).deleteUpload("/files/tus-id");
        verify(sessionRepository).updateStatus(SESSION_ID, UploadStatus.ABORTED);
    }

    @Test
    void 세션_없으면_SessionNotFoundException() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> useCase.abort(SESSION_ID, OWNER));
    }

    private UploadSession session(String tusUploadUri) {
        return new UploadSession(SESSION_ID, OWNER, BackupType.DB, "dump.zip",
                1024L, 512L, null, tusUploadUri,
                UploadStatus.UPLOADING, Instant.now().plusSeconds(3600), Instant.now());
    }
}
