package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.HospitalQuota;

import java.util.List;
import java.util.Optional;

public interface QuotaRepository {
    Optional<HospitalQuota> findByHospitalId(HospitalId hospitalId);
    List<HospitalQuota> findAll();
    void initializeQuota(HospitalId hospitalId, long limitBytes);
    void updateLimit(HospitalId hospitalId, long limitBytes);
    void addUsage(HospitalId hospitalId, long bytes);
    void subtractUsage(HospitalId hospitalId, long bytes);
    void resetUsage(HospitalId hospitalId);
}
