package com.intocns.backup.application;

import com.intocns.backup.domain.exception.IntegrityCheckFailedException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalizeUploadUseCaseTest {

    @Mock UploadSessionRepository sessionRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock QuotaRepository quotaRepository;
    @Mock BackupStoragePort storage;
    @Mock ChunkedUploadProtocol protocol;

    FinalizeUploadUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final UUID SESSION_ID = UUID.randomUUID();
    static final String EXPECTED_SHA = "abc123";
    static final Path DATA_PATH = Path.of("/tmp/data");

    @BeforeEach
    void setUp() {
        useCase = new FinalizeUploadUseCase(sessionRepository, artifactRepository,
                quotaRepository, storage, protocol, 30L, 90L);
    }

    @Test
    void 정상_완료_SHA256_일치() throws IOException {
        UploadSession session = session(EXPECTED_SHA);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn(EXPECTED_SHA);
        given(storage.promoteToArtifacts(any(), any(), any(), any())).willReturn(Path.of("/artifacts/dump.zip"));
        given(artifactRepository.save(any())).willAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> useCase.finalize(SESSION_ID));

        verify(artifactRepository).save(any());
        verify(quotaRepository).addUsage(HOSPITAL_ID, session.totalSize());
        verify(sessionRepository).updateStatus(SESSION_ID, UploadStatus.COMPLETED);
        verify(protocol).deleteUpload(session.tusUploadUri());
    }

    @Test
    void expectedSha256_null이면_검증_생략() throws IOException {
        UploadSession session = session(null);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn("anyHash");
        given(storage.promoteToArtifacts(any(), any(), any(), any())).willReturn(Path.of("/artifacts/dump.zip"));
        given(artifactRepository.save(any())).willAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> useCase.finalize(SESSION_ID));
        verify(sessionRepository).updateStatus(SESSION_ID, UploadStatus.COMPLETED);
    }

    @Test
    void SHA256_불일치시_IntegrityCheckFailedException() throws IOException {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session(EXPECTED_SHA)));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn("wrongHash");

        assertThrows(IntegrityCheckFailedException.class, () -> useCase.finalize(SESSION_ID));
        verify(sessionRepository, never()).updateStatus(any(), any());
    }

    @Test
    void 세션_없으면_SessionNotFoundException() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());
        assertThrows(SessionNotFoundException.class, () -> useCase.finalize(SESSION_ID));
    }

    private UploadSession session(String expectedSha256) {
        return new UploadSession(SESSION_ID, HOSPITAL_ID, BackupType.DB, "dump.zip",
                1024L, 1024L, expectedSha256, "/files/tus-id",
                UploadStatus.UPLOADING, Instant.now().plusSeconds(3600), Instant.now());
    }
}
