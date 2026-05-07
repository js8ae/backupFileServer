package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateHospitalUseCaseTest {

    @Mock HospitalRepository hospitalRepository;
    @Mock QuotaRepository quotaRepository;

    UpdateHospitalUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        useCase = new UpdateHospitalUseCase(hospitalRepository, quotaRepository);
    }

    @Test
    void 이름_변경() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(existing()));

        Hospital result = useCase.update(HOSPITAL_ID,
                new UpdateHospitalUseCase.Command("새이름", null, null, null, null));

        assertEquals("새이름", result.name());
        assertEquals(1_073_741_824L, result.maxStorageBytes());
        verify(hospitalRepository).save(result);
        verifyNoInteractions(quotaRepository);
    }

    @Test
    void maxStorageBytes_변경시_quota_limit도_갱신() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(existing()));

        useCase.update(HOSPITAL_ID,
                new UpdateHospitalUseCase.Command(null, null, null, 2_000_000_000L, null));

        verify(quotaRepository).updateLimit(HOSPITAL_ID, 2_000_000_000L);
    }

    @Test
    void maxStorageBytes_미변경시_quota_갱신_안함() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(existing()));

        useCase.update(HOSPITAL_ID,
                new UpdateHospitalUseCase.Command("새이름", null, null, null, null));

        verifyNoInteractions(quotaRepository);
    }

    @Test
    void null_필드는_기존값_유지() {
        Hospital exist = existing();
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(exist));

        Hospital result = useCase.update(HOSPITAL_ID,
                new UpdateHospitalUseCase.Command(null, null, null, null, null));

        assertEquals(exist.name(), result.name());
        assertEquals(exist.licenseStartAt(), result.licenseStartAt());
        assertEquals(exist.licenseEndAt(), result.licenseEndAt());
        assertEquals(exist.maxStorageBytes(), result.maxStorageBytes());
        assertEquals(exist.active(), result.active());
    }

    @Test
    void 병원_없으면_HospitalNotFoundException() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.empty());

        assertThrows(HospitalNotFoundException.class, () ->
                useCase.update(HOSPITAL_ID, new UpdateHospitalUseCase.Command("이름", null, null, null, null)));
    }

    private Hospital existing() {
        return new Hospital(HOSPITAL_ID, "기존이름",
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS),
                1_073_741_824L, true, NOW, NOW);
    }
}
