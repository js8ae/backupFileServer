package com.intocns.backup;

import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUploadSessionRepositoryIT extends AbstractIT {

    @Autowired UploadSessionRepository sessionRepository;
    @Autowired HospitalRepository hospitalRepository;

    static final HospitalId HOSPITAL_ID = new HospitalId(5001L);
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUpHospital() {
        if (hospitalRepository.findById(HOSPITAL_ID).isEmpty()) {
            hospitalRepository.save(new Hospital(HOSPITAL_ID, "테스트병원",
                    NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS),
                    1_073_741_824L, true, NOW, NOW));
        }
    }

    @Test
    void 세션_저장_후_조회() {
        UploadSession session = session(NOW.plusSeconds(3600));
        sessionRepository.save(session);

        Optional<UploadSession> found = sessionRepository.findById(session.id());

        assertTrue(found.isPresent());
        assertEquals(HOSPITAL_ID, found.get().hospitalId());
        assertEquals(BackupType.DB, found.get().type());
        assertEquals(UploadStatus.INITIATED, found.get().status());
        assertEquals(0L, found.get().currentOffset());
    }

    @Test
    void 상태_업데이트() {
        UploadSession session = session(NOW.plusSeconds(3600));
        sessionRepository.save(session);

        sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);

        Optional<UploadSession> found = sessionRepository.findById(session.id());
        assertTrue(found.isPresent());
        assertEquals(UploadStatus.ABORTED, found.get().status());
    }

    @Test
    void 오프셋_업데이트() {
        UploadSession session = session(NOW.plusSeconds(3600));
        sessionRepository.save(session);

        sessionRepository.updateOffset(session.id(), 512L);

        Optional<UploadSession> found = sessionRepository.findById(session.id());
        assertTrue(found.isPresent());
        assertEquals(512L, found.get().currentOffset());
        assertEquals(UploadStatus.UPLOADING, found.get().status());
    }

    @Test
    void 만료된_세션_조회() {
        UploadSession expired = session(NOW.minus(1, ChronoUnit.HOURS));
        UploadSession active = session(NOW.plusSeconds(3600));
        sessionRepository.save(expired);
        sessionRepository.save(active);

        List<UploadSession> result = sessionRepository.findExpiredBefore(NOW);

        assertTrue(result.stream().anyMatch(s -> s.id().equals(expired.id())));
        assertTrue(result.stream().noneMatch(s -> s.id().equals(active.id())));
    }

    @Test
    void TUS_URI로_조회() {
        UploadSession session = session(NOW.plusSeconds(3600));
        sessionRepository.save(session);
        sessionRepository.updateTusUploadUri(session.id(), "/files/tus-abc");

        Optional<UploadSession> found = sessionRepository.findByTusUploadUri("/files/tus-abc");

        assertTrue(found.isPresent());
        assertEquals(session.id(), found.get().id());
    }

    @Test
    void 병원별_세션_조회() {
        UploadSession s1 = session(NOW.plusSeconds(3600));
        UploadSession s2 = session(NOW.plusSeconds(3600));
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<UploadSession> result = sessionRepository.findByHospitalId(HOSPITAL_ID);

        assertTrue(result.size() >= 2);
        assertTrue(result.stream().allMatch(s -> s.hospitalId().equals(HOSPITAL_ID)));
    }

    private UploadSession session(Instant expiresAt) {
        return new UploadSession(UUID.randomUUID(), HOSPITAL_ID, BackupType.DB,
                "dump.zip", 1024L, 0L, null, null,
                UploadStatus.INITIATED, expiresAt, NOW);
    }
}
