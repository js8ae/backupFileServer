package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;

import java.util.Optional;

public interface HospitalRepository {
    Optional<Hospital> findById(HospitalId id);
    Hospital save(Hospital hospital);
}
