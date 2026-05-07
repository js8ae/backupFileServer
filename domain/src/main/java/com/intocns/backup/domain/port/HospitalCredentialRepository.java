package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.CredentialInfo;
import com.intocns.backup.domain.model.HospitalId;

import java.time.Instant;
import java.util.List;

public interface HospitalCredentialRepository {
    void save(HospitalId hospitalId, String clientId, String clientSecretHash, Instant createdAt);
    List<CredentialInfo> findAllActiveByHospitalId(HospitalId hospitalId);
    boolean revoke(HospitalId hospitalId, String clientId, Instant revokedAt);
}
