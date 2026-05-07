package com.intocns.backup.domain.model;

public record HospitalId(String value) {
    public HospitalId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HospitalId must not be blank");
        }
    }
}
