package com.intocns.backup.api.admin.dto;

import com.intocns.backup.domain.model.Hospital;

import java.time.Instant;

public record HospitalResponse(
        long cocode,
        String name,
        Instant licenseStartAt,
        Instant licenseEndAt,
        long maxStorageBytes,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static HospitalResponse from(Hospital hospital) {
        return new HospitalResponse(
                hospital.id().cocode(),
                hospital.name(),
                hospital.licenseStartAt(),
                hospital.licenseEndAt(),
                hospital.maxStorageBytes(),
                hospital.active(),
                hospital.createdAt(),
                hospital.updatedAt()
        );
    }
}
