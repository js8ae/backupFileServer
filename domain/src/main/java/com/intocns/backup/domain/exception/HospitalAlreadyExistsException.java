package com.intocns.backup.domain.exception;

import com.intocns.backup.domain.model.HospitalId;

public class HospitalAlreadyExistsException extends BackupDomainException {
    public HospitalAlreadyExistsException(HospitalId hospitalId) {
        super("Hospital already exists: cocode=" + hospitalId.cocode());
    }
}
