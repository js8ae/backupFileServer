package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalAlreadyExistsException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegisterHospitalUseCaseTest {

    @Mock HospitalRepository hospitalRepository;
    @Mock QuotaRepository quotaRepository;

    RegisterHospitalUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final Instant NOW = Instant.now();
    static final long MAX_STORAGE = 10_737_418_240L; // 10GB

    @BeforeEach
    void setUp() {
        useCase = new RegisterHospitalUseCase(hospitalRepository, quotaRepository);
    }

    @Test
    void 정상_등록() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.empty());
        given(hospitalRepository.save(any())).willAnswer(i -> i.getArgument(0));

        Hospital result = useCase.register(command());

        assertEquals(HOSPITAL_ID, result.id());
        assertTrue(result.active());

        ArgumentCaptor<HospitalId> idCaptor = ArgumentCaptor.forClass(HospitalId.class);
        ArgumentCaptor<Long> limitCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quotaRepository).initializeQuota(idCaptor.capture(), limitCaptor.capture());
        assertEquals(HOSPITAL_ID, idCaptor.getValue());
        assertEquals(MAX_STORAGE, limitCaptor.getValue());
    }

    @Test
    void 이미_존재하는_병원이면_HospitalAlreadyExistsException() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(existingHospital()));

        assertThrows(HospitalAlreadyExistsException.class, () -> useCase.register(command()));
    }

    private RegisterHospitalUseCase.Command command() {
        return new RegisterHospitalUseCase.Command(
                HOSPITAL_ID.cocode(), "서울병원",
                NOW.minus(1, ChronoUnit.DAYS),
                NOW.plus(365, ChronoUnit.DAYS),
                MAX_STORAGE
        );
    }

    private Hospital existingHospital() {
        return new Hospital(HOSPITAL_ID, "서울병원", NOW, NOW.plus(365, ChronoUnit.DAYS),
                MAX_STORAGE, true, NOW, NOW);
    }
}
