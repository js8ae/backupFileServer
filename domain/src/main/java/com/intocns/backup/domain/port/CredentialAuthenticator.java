package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.HospitalId;

import java.util.Optional;

public interface CredentialAuthenticator {
    Optional<HospitalId> authenticate(String clientId, String clientSecret);
}
