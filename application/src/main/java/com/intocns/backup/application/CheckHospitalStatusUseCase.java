package com.intocns.backup.application;

import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CheckHospitalStatusUseCase {

    private final HospitalRepository hospitalRepository;

    public CheckHospitalStatusUseCase(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    public boolean isEnabled(long cocode) {
        Optional<Hospital> hospital = hospitalRepository.findById(new HospitalId(cocode));
        return hospital.map(h -> h.isLicenseValid(Instant.now())).orElse(false);
    }
}
