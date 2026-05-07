package com.intocns.backup.domain.exception;

import com.intocns.backup.domain.model.HospitalId;

public class HospitalNotFoundException extends BackupDomainException {
    public HospitalNotFoundException(HospitalId hospitalId) {
        super("Hospital not found: cocode=" + hospitalId.cocode());
    }
}
