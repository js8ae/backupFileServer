package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@Transactional
public class IssueCredentialUseCase {

    public record IssuedCredential(String clientId, String clientSecret) {}

    private final HospitalRepository hospitalRepository;
    private final HospitalCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public IssueCredentialUseCase(HospitalRepository hospitalRepository,
                                  HospitalCredentialRepository credentialRepository,
                                  PasswordHasher passwordHasher) {
        this.hospitalRepository = hospitalRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
    }

    public IssuedCredential issue(HospitalId hospitalId) {
        hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new HospitalNotFoundException(hospitalId));

        String clientId = "hosp_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = generateSecret();

        credentialRepository.save(hospitalId, clientId, passwordHasher.hash(clientSecret), Instant.now());

        return new IssuedCredential(clientId, clientSecret);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
