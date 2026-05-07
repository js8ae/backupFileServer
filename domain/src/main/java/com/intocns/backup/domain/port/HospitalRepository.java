package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;

import java.util.List;
import java.util.Optional;

public interface HospitalRepository {
    Optional<Hospital> findById(HospitalId id);
    List<Hospital> findAll();
    Hospital save(Hospital hospital);
}
