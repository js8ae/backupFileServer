package com.intocns.backup.domain.port;

import com.intocns.backup.domain.model.HospitalId;

public interface TokenParser {
    HospitalId parse(String token);
}
