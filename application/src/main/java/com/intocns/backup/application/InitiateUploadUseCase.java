package com.intocns.backup.application;

import com.intocns.backup.domain.exception.LicenseExpiredException;
import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
public class InitiateUploadUseCase {

    private final HospitalRepository hospitalRepository;
    private final UploadSessionRepository sessionRepository;
    private final QuotaRepository quotaRepository;
    private final long sessionTtlHours;

    public InitiateUploadUseCase(
            HospitalRepository hospitalRepository,
            UploadSessionRepository sessionRepository,
            QuotaRepository quotaRepository,
            @Value("${backup.session.ttl-hours:24}") long sessionTtlHours) {
        this.hospitalRepository = hospitalRepository;
        this.sessionRepository = sessionRepository;
        this.quotaRepository = quotaRepository;
        this.sessionTtlHours = sessionTtlHours;
    }

    public record Command(
        HospitalId hospitalId,
        BackupType type,
        String originalFilename,
        long totalSize,
        String expectedSha256
    ) {}

    public UUID initiate(Command command) {
        Instant now = Instant.now();

        Hospital hospital = hospitalRepository.findById(command.hospitalId())
                .orElseThrow(() -> new SessionNotFoundException(null)); // 미등록 병원

        if (!hospital.isLicenseValid(now)) {
            throw new LicenseExpiredException(command.hospitalId());
        }

        quotaRepository.findByHospitalId(command.hospitalId()).ifPresent(quota -> {
            if (!quota.canAccommodate(command.totalSize())) {
                throw new QuotaExceededException(command.hospitalId(), quota.usedBytes(), quota.limitBytes());
            }
        });

        UploadSession session = new UploadSession(
            UUID.randomUUID(),
            command.hospitalId(),
            command.type(),
            command.originalFilename(),
            command.totalSize(),
            0L,
            command.expectedSha256(),
            null,
            UploadStatus.INITIATED,
            now.plus(sessionTtlHours, ChronoUnit.HOURS),
            now
        );
        sessionRepository.save(session);
        return session.id();
    }
}
