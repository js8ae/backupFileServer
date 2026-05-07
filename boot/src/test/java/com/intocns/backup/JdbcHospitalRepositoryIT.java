package com.intocns.backup;

import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcHospitalRepositoryIT extends AbstractIT {

    @Autowired HospitalRepository hospitalRepository;

    static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    @Test
    void 병원_저장_후_조회() {
        Hospital hospital = hospital(2001L, "서울대병원");
        hospitalRepository.save(hospital);

        Optional<Hospital> found = hospitalRepository.findById(new HospitalId(2001L));

        assertTrue(found.isPresent());
        assertEquals("서울대병원", found.get().name());
        assertEquals(2001L, found.get().id().cocode());
        assertTrue(found.get().active());
    }

    @Test
    void 전체_목록_조회() {
        hospitalRepository.save(hospital(3001L, "병원A"));
        hospitalRepository.save(hospital(3002L, "병원B"));

        List<Hospital> all = hospitalRepository.findAll();

        assertTrue(all.stream().anyMatch(h -> h.id().cocode() == 3001L));
        assertTrue(all.stream().anyMatch(h -> h.id().cocode() == 3002L));
    }

    @Test
    void 존재하지_않는_병원_조회시_empty() {
        Optional<Hospital> found = hospitalRepository.findById(new HospitalId(99999L));
        assertTrue(found.isEmpty());
    }

    @Test
    void 병원_정보_업데이트() {
        hospitalRepository.save(hospital(4001L, "원래이름"));

        Hospital updated = new Hospital(new HospitalId(4001L), "변경된이름",
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS),
                5_000_000L, true, NOW, NOW);
        hospitalRepository.save(updated);

        Optional<Hospital> found = hospitalRepository.findById(new HospitalId(4001L));
        assertTrue(found.isPresent());
        assertEquals("변경된이름", found.get().name());
        assertEquals(5_000_000L, found.get().maxStorageBytes());
    }

    private Hospital hospital(long cocode, String name) {
        return new Hospital(new HospitalId(cocode), name,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS),
                1_073_741_824L, true, NOW, NOW);
    }
}
