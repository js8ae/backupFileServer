package com.intocns.backup.application;

import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.model.*;
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

    private final UploadSessionRepository sessionRepository;
    private final QuotaRepository quotaRepository;
    private final long sessionTtlHours;

    public InitiateUploadUseCase(
            UploadSessionRepository sessionRepository,
            QuotaRepository quotaRepository,
            @Value("${backup.session.ttl-hours:24}") long sessionTtlHours) {
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
            Instant.now().plus(sessionTtlHours, ChronoUnit.HOURS),
            Instant.now()
        );
        sessionRepository.save(session);
        return session.id();
    }
}
