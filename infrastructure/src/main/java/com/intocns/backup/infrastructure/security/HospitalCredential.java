package com.intocns.backup.infrastructure.security;

import com.intocns.backup.domain.model.HospitalId;

public record HospitalCredential(HospitalId hospitalId, String clientId, String clientSecretHash) {
}
