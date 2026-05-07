package com.intocns.backup.domain.exception;

import com.intocns.backup.domain.model.HospitalId;

public class QuotaExceededException extends BackupDomainException {
    public QuotaExceededException(HospitalId hospitalId, long usedBytes, long limitBytes) {
        super("Quota exceeded for hospital cocode=%d used=%d limit=%d".formatted(
                hospitalId.cocode(), usedBytes, limitBytes));
    }
}
