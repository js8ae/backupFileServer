package com.intocns.backup.application;

import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkTusUploadUseCaseTest {

    @Mock UploadSessionRepository sessionRepository;

    LinkTusUploadUseCase useCase;

    static final HospitalId OWNER = new HospitalId(1001L);
    static final HospitalId OTHER = new HospitalId(9999L);
    static final UUID SESSION_ID = UUID.randomUUID();
    static final String TUS_URI = "/files/tus-abc";

    @BeforeEach
    void setUp() {
        useCase = new LinkTusUploadUseCase(sessionRepository);
    }

    @Test
    void 정상_연결() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session()));

        useCase.link(SESSION_ID, TUS_URI, OWNER);

        verify(sessionRepository).updateTusUploadUri(SESSION_ID, TUS_URI);
    }

    @Test
    void 세션_없으면_SessionNotFoundException() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> useCase.link(SESSION_ID, TUS_URI, OWNER));
        verify(sessionRepository, never()).updateTusUploadUri(any(), any());
    }

    @Test
    void 다른_병원이면_UnauthorizedSessionAccessException() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session()));

        assertThrows(UnauthorizedSessionAccessException.class, () -> useCase.link(SESSION_ID, TUS_URI, OTHER));
        verify(sessionRepository, never()).updateTusUploadUri(any(), any());
    }

    private UploadSession session() {
        return new UploadSession(SESSION_ID, OWNER, BackupType.DB, "dump.zip",
                1024L, 0L, null, null,
                UploadStatus.INITIATED, Instant.now().plusSeconds(3600), Instant.now());
    }
}
