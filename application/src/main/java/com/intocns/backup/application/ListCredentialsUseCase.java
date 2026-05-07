package com.intocns.backup.application;

import com.intocns.backup.domain.model.CredentialInfo;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListCredentialsUseCase {

    private final HospitalCredentialRepository credentialRepository;

    public ListCredentialsUseCase(HospitalCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    public List<CredentialInfo> list(HospitalId hospitalId) {
        return credentialRepository.findAllActiveByHospitalId(hospitalId);
    }
}
