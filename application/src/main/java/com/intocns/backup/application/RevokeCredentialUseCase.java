package com.intocns.backup.application;

import com.intocns.backup.domain.exception.CredentialNotFoundException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class RevokeCredentialUseCase {

    private final HospitalCredentialRepository credentialRepository;

    public RevokeCredentialUseCase(HospitalCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    public void revoke(HospitalId hospitalId, String clientId) {
        boolean found = credentialRepository.revoke(hospitalId, clientId, Instant.now());
        if (!found) {
            throw new CredentialNotFoundException(clientId);
        }
    }
}
