package com.intocns.backup.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class HospitalTest {

    private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");
    private static final Instant PAST = NOW.minus(30, ChronoUnit.DAYS);
    private static final Instant FUTURE = NOW.plus(30, ChronoUnit.DAYS);

    @Test
    void 활성_병원이고_라이센스_기간_내이면_유효() {
        Hospital hospital = hospital(true, PAST, FUTURE);
        assertTrue(hospital.isLicenseValid(NOW));
    }

    @Test
    void 비활성_병원은_라이센스_기간_내여도_무효() {
        Hospital hospital = hospital(false, PAST, FUTURE);
        assertFalse(hospital.isLicenseValid(NOW));
    }

    @Test
    void 라이센스_시작_전이면_무효() {
        Hospital hospital = hospital(true, FUTURE, FUTURE.plus(60, ChronoUnit.DAYS));
        assertFalse(hospital.isLicenseValid(NOW));
    }

    @Test
    void 라이센스_종료_후이면_무효() {
        Hospital hospital = hospital(true, PAST.minus(60, ChronoUnit.DAYS), PAST);
        assertFalse(hospital.isLicenseValid(NOW));
    }

    @Test
    void 라이센스_종료_시각_정확히는_무효() {
        Hospital hospital = hospital(true, PAST, NOW);
        assertFalse(hospital.isLicenseValid(NOW));
    }

    private Hospital hospital(boolean active, Instant start, Instant end) {
        return new Hospital(new HospitalId(1001L), "테스트병원", start, end,
                1_000_000L, active, NOW, NOW);
    }
}
