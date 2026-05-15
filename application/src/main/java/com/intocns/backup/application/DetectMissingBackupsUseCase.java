package com.intocns.backup.application;

import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.ArtifactRepository.LatestArtifactStat;
import com.intocns.backup.domain.port.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DetectMissingBackupsUseCase {

    private final HospitalRepository hospitalRepository;
    private final ArtifactRepository artifactRepository;
    private final long dbMissingHours;
    private final long fileMissingHours;

    public DetectMissingBackupsUseCase(
            HospitalRepository hospitalRepository,
            ArtifactRepository artifactRepository,
            @Value("${backup.monitoring.db-missing-hours:25}") long dbMissingHours,
            @Value("${backup.monitoring.file-missing-hours:0}") long fileMissingHours) {
        this.hospitalRepository = hospitalRepository;
        this.artifactRepository = artifactRepository;
        this.dbMissingHours = dbMissingHours;
        this.fileMissingHours = fileMissingHours;
    }

    /** 누락된 백업이 있는 활성 병원만 반환. */
    public List<MissingInfo> detect() {
        Instant now = Instant.now();

        // 활성 병원 목록
        List<Hospital> activeHospitals = hospitalRepository.findAll().stream()
                .filter(Hospital::active)
                .toList();

        // 병원·타입별 최신 백업 시각 — 단일 GROUP BY 쿼리
        Map<HospitalId, Map<BackupType, Instant>> latestMap = buildLatestMap(
                artifactRepository.findLatestStatPerHospitalAndType());

        return activeHospitals.stream()
                .map(h -> toMissingInfo(h, latestMap.getOrDefault(h.id(), Map.of()), now))
                .filter(MissingInfo::anyMissing)
                .toList();
    }

    public long dbMissingHours() { return dbMissingHours; }
    public long fileMissingHours() { return fileMissingHours; }

    private MissingInfo toMissingInfo(Hospital hospital,
                                      Map<BackupType, Instant> latest,
                                      Instant now) {
        Instant dbAt = latest.get(BackupType.DB);
        Instant fileAt = latest.get(BackupType.FILE);

        boolean dbMissing = dbMissingHours > 0 && isMissing(dbAt, now, dbMissingHours);
        boolean fileMissing = fileMissingHours > 0 && isMissing(fileAt, now, fileMissingHours);

        return new MissingInfo(
                hospital,
                dbMissing, dbAt, hoursSince(dbAt, now),
                fileMissing, fileAt, hoursSince(fileAt, now)
        );
    }

    private static boolean isMissing(Instant lastAt, Instant now, long thresholdHours) {
        if (lastAt == null) return true;
        return lastAt.isBefore(now.minus(thresholdHours, ChronoUnit.HOURS));
    }

    private static Long hoursSince(Instant lastAt, Instant now) {
        if (lastAt == null) return null;
        return ChronoUnit.HOURS.between(lastAt, now);
    }

    private static Map<HospitalId, Map<BackupType, Instant>> buildLatestMap(
            List<LatestArtifactStat> stats) {
        Map<HospitalId, Map<BackupType, Instant>> map = new HashMap<>();
        for (LatestArtifactStat stat : stats) {
            map.computeIfAbsent(stat.hospitalId(), k -> new EnumMap<>(BackupType.class))
               .put(stat.type(), stat.latestCreatedAt());
        }
        return map;
    }

    public record MissingInfo(
            Hospital hospital,
            boolean dbMissing,
            Instant dbLastBackupAt,
            Long dbHoursSinceLastBackup,
            boolean fileMissing,
            Instant fileLastBackupAt,
            Long fileHoursSinceLastBackup
    ) {
        public boolean anyMissing() { return dbMissing || fileMissing; }
    }
}
