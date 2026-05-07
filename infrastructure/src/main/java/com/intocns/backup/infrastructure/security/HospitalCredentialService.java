package com.intocns.backup.infrastructure.security;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.CredentialAuthenticator;
import com.intocns.backup.infrastructure.db.JdbcHospitalCredentialRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HospitalCredentialService implements CredentialAuthenticator {

    private final JdbcHospitalCredentialRepository credentialRepository;
    private final BCryptPasswordEncoder encoder;

    public HospitalCredentialService(JdbcHospitalCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
        this.encoder = new BCryptPasswordEncoder();
    }

    @Override
    public Optional<HospitalId> authenticate(String clientId, String clientSecret) {
        return credentialRepository.findActiveByClientId(clientId)
                .filter(cred -> encoder.matches(clientSecret, cred.clientSecretHash()))
                .map(HospitalCredential::hospitalId);
    }
}
