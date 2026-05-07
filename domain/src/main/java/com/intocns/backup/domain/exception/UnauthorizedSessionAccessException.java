package com.intocns.backup.domain.exception;

import com.intocns.backup.domain.model.HospitalId;

import java.util.UUID;

public class UnauthorizedSessionAccessException extends BackupDomainException {
    public UnauthorizedSessionAccessException(UUID sessionId, HospitalId requestingHospital) {
        super("Hospital=%s is not authorized to access session=%s".formatted(
                requestingHospital.value(), sessionId));
    }
}
