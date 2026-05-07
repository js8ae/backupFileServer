package com.intocns.backup.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HospitalQuotaTest {

    private static final HospitalId ID = new HospitalId(1001L);

    @Test
    void 사용량이_한도_미만이면_수용_가능() {
        HospitalQuota quota = new HospitalQuota(ID, 500L, 1000L);
        assertTrue(quota.canAccommodate(499L));
    }

    @Test
    void 추가_후_사용량이_한도와_같으면_수용_가능() {
        HospitalQuota quota = new HospitalQuota(ID, 500L, 1000L);
        assertTrue(quota.canAccommodate(500L));
    }

    @Test
    void 추가_후_사용량이_한도_초과이면_수용_불가() {
        HospitalQuota quota = new HospitalQuota(ID, 500L, 1000L);
        assertFalse(quota.canAccommodate(501L));
    }

    @Test
    void 잔여_용량은_한도에서_사용량을_뺀_값() {
        HospitalQuota quota = new HospitalQuota(ID, 300L, 1000L);
        assertEquals(700L, quota.remainingBytes());
    }

    @Test
    void 한도_초과_시_잔여_용량은_음수() {
        HospitalQuota quota = new HospitalQuota(ID, 1200L, 1000L);
        assertEquals(-200L, quota.remainingBytes());
    }
}
