package com.intocns.backup.application;

import com.intocns.backup.domain.exception.CredentialNotFoundException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RevokeCredentialUseCaseTest {

    @Mock HospitalCredentialRepository credentialRepository;

    RevokeCredentialUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);
    static final String CLIENT_ID = "hosp_abc123";

    @BeforeEach
    void setUp() {
        useCase = new RevokeCredentialUseCase(credentialRepository);
    }

    @Test
    void 정상_폐기() {
        given(credentialRepository.revoke(eq(HOSPITAL_ID), eq(CLIENT_ID), any(Instant.class)))
                .willReturn(true);

        assertDoesNotThrow(() -> useCase.revoke(HOSPITAL_ID, CLIENT_ID));
        verify(credentialRepository).revoke(eq(HOSPITAL_ID), eq(CLIENT_ID), any(Instant.class));
    }

    @Test
    void 존재하지_않는_크리덴셜이면_CredentialNotFoundException() {
        given(credentialRepository.revoke(eq(HOSPITAL_ID), eq(CLIENT_ID), any(Instant.class)))
                .willReturn(false);

        assertThrows(CredentialNotFoundException.class, () -> useCase.revoke(HOSPITAL_ID, CLIENT_ID));
    }
}
