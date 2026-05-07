package com.intocns.backup.domain.model;

public record HospitalQuota(
    HospitalId hospitalId,
    long usedBytes,
    long limitBytes
) {
    public boolean canAccommodate(long additionalBytes) {
        return usedBytes + additionalBytes <= limitBytes;
    }

    public long remainingBytes() {
        return limitBytes - usedBytes;
    }
}
