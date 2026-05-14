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
import java.util.List;
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
    @Mock AuditLogPort auditLogPort;

    FinalizeUploadUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final UUID SESSION_ID = UUID.randomUUID();
    static final String EXPECTED_SHA = "abc123";
    static final Path DATA_PATH = Path.of("/tmp/data");

    @BeforeEach
    void setUp() {
        useCase = new FinalizeUploadUseCase(sessionRepository, artifactRepository,
                quotaRepository, storage, protocol, auditLogPort);
    }

    @Test
    void 정상_완료_SHA256_일치() throws IOException {
        UploadSession session = session(EXPECTED_SHA, BackupType.FILE);
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
        UploadSession session = session(null, BackupType.FILE);
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
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session(EXPECTED_SHA, BackupType.FILE)));
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

    @Test
    void DB_타입_3개_초과시_가장_오래된것_삭제() throws IOException {
        UploadSession session = session(null, BackupType.DB);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn("anyHash");
        given(storage.promoteToArtifacts(any(), any(), any(), any())).willReturn(Path.of("/artifacts/new.zip"));
        given(artifactRepository.save(any())).willAnswer(i -> i.getArgument(0));

        BackupArtifact old1 = artifact(BackupType.DB, "/artifacts/old1.zip", 100L, Instant.now().minusSeconds(300));
        BackupArtifact old2 = artifact(BackupType.DB, "/artifacts/old2.zip", 200L, Instant.now().minusSeconds(200));
        BackupArtifact old3 = artifact(BackupType.DB, "/artifacts/old3.zip", 300L, Instant.now().minusSeconds(100));
        given(artifactRepository.findByHospitalIdAndType(HOSPITAL_ID, BackupType.DB))
                .willReturn(List.of(old1, old2, old3));

        useCase.finalize(SESSION_ID);

        // 3개가 이미 있으므로 1개(old1)를 삭제 후 새 것 저장
        verify(storage).moveToTrash(Path.of(old1.storagePath()));
        verify(quotaRepository).subtractUsage(HOSPITAL_ID, old1.sizeBytes());
        verify(artifactRepository).markPurged(eq(old1.id()), any());
        verify(storage, never()).moveToTrash(Path.of(old2.storagePath()));
        verify(artifactRepository).save(any());
    }

    @Test
    void DB_타입_2개_이하면_삭제_없이_저장() throws IOException {
        UploadSession session = session(null, BackupType.DB);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn("anyHash");
        given(storage.promoteToArtifacts(any(), any(), any(), any())).willReturn(Path.of("/artifacts/new.zip"));
        given(artifactRepository.save(any())).willAnswer(i -> i.getArgument(0));

        BackupArtifact old1 = artifact(BackupType.DB, "/artifacts/old1.zip", 100L, Instant.now().minusSeconds(200));
        BackupArtifact old2 = artifact(BackupType.DB, "/artifacts/old2.zip", 200L, Instant.now().minusSeconds(100));
        given(artifactRepository.findByHospitalIdAndType(HOSPITAL_ID, BackupType.DB))
                .willReturn(List.of(old1, old2));

        useCase.finalize(SESSION_ID);

        verify(storage, never()).moveToTrash(any());
        verify(artifactRepository).save(any());
    }

    @Test
    void 저장된_artifact의_expiresAt은_null() throws IOException {
        UploadSession session = session(null, BackupType.FILE);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(protocol.resolveDataPath(any())).willReturn(DATA_PATH);
        given(storage.sha256(DATA_PATH)).willReturn("anyHash");
        given(storage.promoteToArtifacts(any(), any(), any(), any())).willReturn(Path.of("/artifacts/file.zip"));

        var captor = org.mockito.ArgumentCaptor.forClass(BackupArtifact.class);
        given(artifactRepository.save(captor.capture())).willAnswer(i -> i.getArgument(0));

        useCase.finalize(SESSION_ID);

        assertNull(captor.getValue().expiresAt());
    }

    private UploadSession session(String expectedSha256, BackupType type) {
        return new UploadSession(SESSION_ID, HOSPITAL_ID, type, "dump.zip",
                1024L, 1024L, expectedSha256, "/files/tus-id",
                UploadStatus.UPLOADING, Instant.now().plusSeconds(3600), Instant.now());
    }

    private BackupArtifact artifact(BackupType type, String path, long sizeBytes, Instant createdAt) {
        return new BackupArtifact(UUID.randomUUID(), HOSPITAL_ID, type, path, sizeBytes,
                "sha256", createdAt, null, null);
    }
}
