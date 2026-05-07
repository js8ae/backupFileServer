package com.intocns.backup.application;

import com.intocns.backup.domain.model.CredentialInfo;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListCredentialsUseCaseTest {

    @Mock HospitalCredentialRepository credentialRepository;

    ListCredentialsUseCase useCase;

    static final HospitalId HOSPITAL_ID = new HospitalId(1001L);

    @BeforeEach
    void setUp() {
        useCase = new ListCredentialsUseCase(credentialRepository);
    }

    @Test
    void 활성_자격증명_목록_반환() {
        List<CredentialInfo> credentials = List.of(
                new CredentialInfo("client-a", Instant.now()),
                new CredentialInfo("client-b", Instant.now())
        );
        given(credentialRepository.findAllActiveByHospitalId(HOSPITAL_ID)).willReturn(credentials);

        List<CredentialInfo> result = useCase.list(HOSPITAL_ID);

        assertEquals(2, result.size());
        assertEquals("client-a", result.get(0).clientId());
    }

    @Test
    void 자격증명_없으면_빈_리스트() {
        given(credentialRepository.findAllActiveByHospitalId(HOSPITAL_ID)).willReturn(List.of());

        List<CredentialInfo> result = useCase.list(HOSPITAL_ID);

        assertTrue(result.isEmpty());
    }
}
