package com.intocns.backup.application;

import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class UpdateHospitalUseCase {

    public record Command(
            String name,
            Instant licenseStartAt,
            Instant licenseEndAt,
            Long maxStorageBytes,
            Boolean active
    ) {}

    private final HospitalRepository hospitalRepository;
    private final QuotaRepository quotaRepository;

    public UpdateHospitalUseCase(HospitalRepository hospitalRepository,
                                 QuotaRepository quotaRepository) {
        this.hospitalRepository = hospitalRepository;
        this.quotaRepository = quotaRepository;
    }

    public Hospital update(HospitalId hospitalId, Command command) {
        Hospital existing = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new HospitalNotFoundException(hospitalId));

        Hospital updated = new Hospital(
                existing.id(),
                command.name() != null ? command.name() : existing.name(),
                command.licenseStartAt() != null ? command.licenseStartAt() : existing.licenseStartAt(),
                command.licenseEndAt() != null ? command.licenseEndAt() : existing.licenseEndAt(),
                command.maxStorageBytes() != null ? command.maxStorageBytes() : existing.maxStorageBytes(),
                command.active() != null ? command.active() : existing.active(),
                existing.createdAt(),
                Instant.now()
        );

        hospitalRepository.save(updated);

        if (command.maxStorageBytes() != null) {
            quotaRepository.updateLimit(hospitalId, command.maxStorageBytes());
        }

        return updated;
    }
}
