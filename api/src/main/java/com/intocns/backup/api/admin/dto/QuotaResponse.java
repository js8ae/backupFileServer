package com.intocns.backup.api.admin.dto;

import com.intocns.backup.domain.model.HospitalQuota;

public record QuotaResponse(long cocode, long usedBytes, long limitBytes, long availableBytes, int usedPercent) {

    public static QuotaResponse from(HospitalQuota quota) {
        long available = Math.max(0, quota.limitBytes() - quota.usedBytes());
        int percent = quota.limitBytes() == 0 ? 0
                : (int) (quota.usedBytes() * 100 / quota.limitBytes());
        return new QuotaResponse(
                quota.hospitalId().cocode(),
                quota.usedBytes(),
                quota.limitBytes(),
                available,
                percent
        );
    }
}
