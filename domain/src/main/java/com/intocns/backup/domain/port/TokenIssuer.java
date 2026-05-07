package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.HospitalId;

public interface TokenIssuer {
    String issue(HospitalId hospitalId);
}
