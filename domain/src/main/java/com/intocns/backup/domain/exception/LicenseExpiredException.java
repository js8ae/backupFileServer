package com.intocns.backup.domain.exception;

import com.intocns.backup.domain.model.HospitalId;

public class LicenseExpiredException extends BackupDomainException {
    public LicenseExpiredException(HospitalId hospitalId) {
        super("License is not valid for hospital cocode=" + hospitalId.cocode());
    }
}
