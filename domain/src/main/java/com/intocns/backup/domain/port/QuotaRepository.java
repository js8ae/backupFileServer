package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.HospitalQuota;

import java.util.Optional;

public interface QuotaRepository {
    Optional<HospitalQuota> findByHospitalId(HospitalId hospitalId);
    void addUsage(HospitalId hospitalId, long bytes);
    void subtractUsage(HospitalId hospitalId, long bytes);
}
