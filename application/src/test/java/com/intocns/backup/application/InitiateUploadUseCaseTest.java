package com.intocns.backup.application;

import com.intocns.backup.domain.exception.LicenseExpiredException;
import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InitiateUploadUseCaseTest {

    @Mock HospitalRepository hospitalRepository;
    @Mock UploadSessionRepository sessionRepository;
    @Mock QuotaRepository quotaRepository;

    InitiateUploadUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new InitiateUploadUseCase(hospitalRepository, sessionRepository, quotaRepository, 24L);
    }

    @Test
    void 정상_세션_생성() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(quotaRepository.findByHospitalId(HOSPITAL_ID))
                .willReturn(Optional.of(new HospitalQuota(HOSPITAL_ID, 0L, 10_000L)));
        ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
        given(sessionRepository.save(captor.capture())).willAnswer(i -> i.getArgument(0));

        UUID sessionId = useCase.initiate(command(5_000L));

        assertNotNull(sessionId);
        UploadSession saved = captor.getValue();
        assertEquals(HOSPITAL_ID, saved.hospitalId());
        assertEquals(BackupType.DB, saved.type());
        assertEquals(UploadStatus.INITIATED, saved.status());
        assertEquals(5_000L, saved.totalSize());
    }

    @Test
    void 라이센스_만료_병원은_예외발생() {
        Hospital expired = new Hospital(HOSPITAL_ID, "병원", NOW.minus(60, ChronoUnit.DAYS),
                NOW.minus(1, ChronoUnit.DAYS), 1_000_000L, true, NOW, NOW);
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(expired));

        assertThrows(LicenseExpiredException.class, () -> useCase.initiate(command(100L)));
    }

    @Test
    void 쿼터_초과시_예외발생() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(quotaRepository.findByHospitalId(HOSPITAL_ID))
                .willReturn(Optional.of(new HospitalQuota(HOSPITAL_ID, 9_900L, 10_000L)));

        assertThrows(QuotaExceededException.class, () -> useCase.initiate(command(200L)));
    }

    @Test
    void 쿼터_정보_없으면_제한_없이_세션_생성() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(activeHospital()));
        given(quotaRepository.findByHospitalId(HOSPITAL_ID)).willReturn(Optional.empty());
        given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> useCase.initiate(command(Long.MAX_VALUE)));
        verify(sessionRepository).save(any());
    }

    private InitiateUploadUseCase.Command command(long size) {
        return new InitiateUploadUseCase.Command(HOSPITAL_ID, BackupType.DB, "dump.zip", size, null);
    }

    private Hospital activeHospital() {
        return new Hospital(HOSPITAL_ID, "병원", NOW.minus(30, ChronoUnit.DAYS),
                NOW.plus(30, ChronoUnit.DAYS), 1_000_000L, true, NOW, NOW);
    }
}
