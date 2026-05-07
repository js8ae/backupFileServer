package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IssueCredentialUseCaseTest {

    @Mock HospitalRepository hospitalRepository;
    @Mock HospitalCredentialRepository credentialRepository;
    @Mock PasswordHasher passwordHasher;

    IssueCredentialUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new IssueCredentialUseCase(hospitalRepository, credentialRepository, passwordHasher);
    }

    @Test
    void 정상_발급() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(hospital()));
        given(passwordHasher.hash(anyString())).willReturn("$2a$10$hashedSecret");

        IssueCredentialUseCase.IssuedCredential result = useCase.issue(HOSPITAL_ID);

        assertNotNull(result.clientId());
        assertTrue(result.clientId().startsWith("hosp_"));
        assertNotNull(result.clientSecret());
        assertFalse(result.clientSecret().isBlank());

        verify(credentialRepository).save(eq(HOSPITAL_ID), eq(result.clientId()),
                eq("$2a$10$hashedSecret"), any(Instant.class));
    }

    @Test
    void 발급마다_다른_clientId_생성() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.of(hospital()));
        given(passwordHasher.hash(anyString())).willReturn("hash");

        String id1 = useCase.issue(HOSPITAL_ID).clientId();
        String id2 = useCase.issue(HOSPITAL_ID).clientId();

        assertNotEquals(id1, id2);
    }

    @Test
    void 미등록_병원이면_HospitalNotFoundException() {
        given(hospitalRepository.findById(HOSPITAL_ID)).willReturn(Optional.empty());

        assertThrows(HospitalNotFoundException.class, () -> useCase.issue(HOSPITAL_ID));
    }

    private Hospital hospital() {
        return new Hospital(HOSPITAL_ID, "병원", NOW.minus(1, ChronoUnit.DAYS),
                NOW.plus(365, ChronoUnit.DAYS), 1_000_000L, true, NOW, NOW);
    }
}
