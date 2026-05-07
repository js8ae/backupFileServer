package com.intocns.backup.domain.model;

public record HospitalId(long cocode) {
    public HospitalId {
        if (cocode <= 0) {
            throw new IllegalArgumentException("HospitalId cocode must be positive: " + cocode);
        }
    }
}
