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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandlePatchUseCaseTest {

    @Mock UploadSessionRepository sessionRepository;
    @Mock ChunkedUploadProtocol protocol;
    @Mock FinalizeUploadUseCase finalizeUploadUseCase;

    HandlePatchUseCase useCase;

    static final HospitalId OWNER = new HospitalId(1001L);
    static final HospitalId OTHER = new HospitalId(9999L);
    static final UUID SESSION_ID = UUID.randomUUID();
    static final String TUS_URI = "/files/tus-abc";

    @BeforeEach
    void setUp() {
        useCase = new HandlePatchUseCase(sessionRepository, protocol, finalizeUploadUseCase);
    }

    // --- verifyAccess ---

    @Test
    void verifyAccess_정상_접근() {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.of(session()));

        assertDoesNotThrow(() -> useCase.verifyAccess(TUS_URI, OWNER));
    }

    @Test
    void verifyAccess_세션_없으면_SessionNotFoundException() {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> useCase.verifyAccess(TUS_URI, OWNER));
    }

    @Test
    void verifyAccess_다른_병원이면_UnauthorizedSessionAccessException() {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.of(session()));

        assertThrows(UnauthorizedSessionAccessException.class, () -> useCase.verifyAccess(TUS_URI, OTHER));
    }

    // --- handle ---

    @Test
    void handle_업로드_진행중이면_offset_갱신() throws IOException {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.of(session()));
        given(protocol.getUploadInfo(TUS_URI))
                .willReturn(Optional.of(new ChunkedUploadProtocol.Info(512L, 1024L, false, Map.of())));

        useCase.handle(TUS_URI, OWNER);

        verify(sessionRepository).updateOffset(SESSION_ID, 512L);
        verifyNoInteractions(finalizeUploadUseCase);
    }

    @Test
    void handle_업로드_완료되면_finalize_호출() throws IOException {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.of(session()));
        given(protocol.getUploadInfo(TUS_URI))
                .willReturn(Optional.of(new ChunkedUploadProtocol.Info(1024L, 1024L, true, Map.of())));

        useCase.handle(TUS_URI, OWNER);

        verify(finalizeUploadUseCase).finalize(SESSION_ID);
        verify(sessionRepository, never()).updateOffset(any(), anyLong());
    }

    @Test
    void handle_세션_없으면_SessionNotFoundException() {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> useCase.handle(TUS_URI, OWNER));
    }

    @Test
    void handle_다른_병원이면_UnauthorizedSessionAccessException() {
        given(sessionRepository.findByTusUploadUri(TUS_URI)).willReturn(Optional.of(session()));

        assertThrows(UnauthorizedSessionAccessException.class, () -> useCase.handle(TUS_URI, OTHER));
        verifyNoInteractions(protocol);
    }

    private UploadSession session() {
        return new UploadSession(SESSION_ID, OWNER, BackupType.DB, "dump.zip",
                1024L, 0L, null, TUS_URI,
                UploadStatus.UPLOADING, Instant.now().plusSeconds(3600), Instant.now());
    }
}
