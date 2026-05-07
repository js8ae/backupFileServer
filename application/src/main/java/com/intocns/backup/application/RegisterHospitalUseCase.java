package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalAlreadyExistsException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class RegisterHospitalUseCase {

    public record Command(
            long cocode,
            String name,
            Instant licenseStartAt,
            Instant licenseEndAt,
            long maxStorageBytes
    ) {}

    private final HospitalRepository hospitalRepository;
    private final QuotaRepository quotaRepository;

    public RegisterHospitalUseCase(HospitalRepository hospitalRepository,
                                   QuotaRepository quotaRepository) {
        this.hospitalRepository = hospitalRepository;
        this.quotaRepository = quotaRepository;
    }

    public Hospital register(Command command) {
        HospitalId hospitalId = new HospitalId(command.cocode());

        if (hospitalRepository.findById(hospitalId).isPresent()) {
            throw new HospitalAlreadyExistsException(hospitalId);
        }

        Instant now = Instant.now();
        Hospital hospital = new Hospital(
                hospitalId,
                command.name(),
                command.licenseStartAt(),
                command.licenseEndAt(),
                command.maxStorageBytes(),
                true,
                now,
                now
        );

        hospitalRepository.save(hospital);
        quotaRepository.initializeQuota(hospitalId, command.maxStorageBytes());

        return hospital;
    }
}
