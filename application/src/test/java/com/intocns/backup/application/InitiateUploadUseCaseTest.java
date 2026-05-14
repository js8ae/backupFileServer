package com.intocns.backup.application;

import com.intocns.backup.domain.exception.LicenseExpiredException;
import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiateUploadUseCaseTest {

    @Mock HospitalRepository hospitalRepository;
    @Mock UploadSessionRepository sessionRepository;
    @Mock QuotaRepository quotaRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock BackupStoragePort storagePort;
    @Mock AuditLogPort auditLogPort;

    InitiateUploadUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new InitiateUploadUseCase(hospitalRepository, sessionRepository,
                quotaRepository, artifactRepository, storagePort, auditLogPort, 24L);
    }

    @Test
    void FILE_정상_세션_생성() throws IOException {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(quotaRepository.findByHospitalId(HOSPITAL_ID))
                .willReturn(Optional.of(new HospitalQuota(HOSPITAL_ID, 0L, 10_000L)));
        ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
        given(sessionRepository.save(captor.capture())).willAnswer(i -> i.getArgument(0));

        UUID sessionId = useCase.initiate(fileCommand(5_000L));

        assertNotNull(sessionId);
        UploadSession saved = captor.getValue();
        assertEquals(HOSPITAL_ID, saved.hospitalId());
        assertEquals(BackupType.FILE, saved.type());
        assertEquals(UploadStatus.INITIATED, saved.status());
        assertEquals(5_000L, saved.totalSize());
    }

    @Test
    void DB_타입은_쿼터_체크_없이_세션_생성() throws IOException {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        UUID sessionId = useCase.initiate(dbCommand(Long.MAX_VALUE));

        assertNotNull(sessionId);
        verify(quotaRepository, never()).findByHospitalId(any());
    }

    @Test
    void 라이센스_만료_병원은_예외발생() {
        Hospital expired = new Hospital(HOSPITAL_ID, "병원", NOW.minus(60, ChronoUnit.DAYS),
                NOW.minus(1, ChronoUnit.DAYS), 1_000_000L, true, NOW, NOW);
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(expired));

        assertThrows(LicenseExpiredException.class, () -> useCase.initiate(fileCommand(100L)));
    }

    @Test
    void FILE_쿼터_초과_evict_불가시_예외발생() throws IOException {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        // used=9900, limit=10000 → 200짜리 업로드 불가, evict 후에도 공간 부족
        given(quotaRepository.findByHospitalId(HOSPITAL_ID))
                .willReturn(Optional.of(new HospitalQuota(HOSPITAL_ID, 9_900L, 10_000L)));
        given(artifactRepository.findByHospitalIdAndType(HOSPITAL_ID, BackupType.FILE))
                .willReturn(List.of()); // 삭제할 artifact 없음

        assertThrows(QuotaExceededException.class, () -> useCase.initiate(fileCommand(200L)));
    }

    @Test
    void FILE_쿼터_초과시_오래된파일_삭제후_업로드() throws IOException {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));

        HospitalQuota overQuota = new HospitalQuota(HOSPITAL_ID, 9_500L, 10_000L);
        HospitalQuota afterEvict = new HospitalQuota(HOSPITAL_ID, 3_500L, 10_000L); // 6000짜리 삭제 후
        given(quotaRepository.findByHospitalId(HOSPITAL_ID))
                .willReturn(Optional.of(overQuota))   // 첫 번째 호출
                .willReturn(Optional.of(afterEvict)); // eviction 후 재조회

        BackupArtifact old = artifact(BackupType.FILE, "/artifacts/old.zip", 6_000L);
        given(artifactRepository.findByHospitalIdAndType(HOSPITAL_ID, BackupType.FILE))
                .willReturn(List.of(old));
        given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        UUID sessionId = useCase.initiate(fileCommand(5_000L));

        assertNotNull(sessionId);
        verify(storagePort).moveToTrash(any());
        verify(quotaRepository).subtractUsage(HOSPITAL_ID, 6_000L);
        verify(artifactRepository).markPurged(eq(old.id()), any());
    }

    @Test
    void 쿼터_정보_없으면_제한_없이_세션_생성() throws IOException {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(quotaRepository.findByHospitalId(HOSPITAL_ID)).willReturn(Optional.empty());
        given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> useCase.initiate(fileCommand(Long.MAX_VALUE)));
        verify(sessionRepository).save(any());
    }

    private InitiateUploadUseCase.Command fileCommand(long size) {
        return new InitiateUploadUseCase.Command(HOSPITAL_ID, BackupType.FILE, "file.zip", size, null);
    }

    private InitiateUploadUseCase.Command dbCommand(long size) {
        return new InitiateUploadUseCase.Command(HOSPITAL_ID, BackupType.DB, "dump.zip", size, null);
    }

    private Hospital activeHospital() {
        return new Hospital(HOSPITAL_ID, "병원", NOW.minus(30, ChronoUnit.DAYS),
                NOW.plus(30, ChronoUnit.DAYS), 1_000_000L, true, NOW, NOW);
    }

    private BackupArtifact artifact(BackupType type, String path, long sizeBytes) {
        return new BackupArtifact(UUID.randomUUID(), HOSPITAL_ID, type, path, sizeBytes,
                "sha256", NOW.minusSeconds(3600), null, null);
    }
}
