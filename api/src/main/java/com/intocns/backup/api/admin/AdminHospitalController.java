package com.intocns.backup.api.admin;

import com.intocns.backup.api.admin.dto.*;
import com.intocns.backup.application.RegisterHospitalUseCase;
import com.intocns.backup.application.UpdateHospitalUseCase;
import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import com.intocns.backup.domain.port.UploadSessionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/admin/hospitals")
public class AdminHospitalController {

    private final RegisterHospitalUseCase registerHospital;
    private final UpdateHospitalUseCase updateHospital;
    private final HospitalRepository hospitalRepository;
    private final QuotaRepository quotaRepository;
    private final UploadSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;

    public AdminHospitalController(RegisterHospitalUseCase registerHospital,
                                   UpdateHospitalUseCase updateHospital,
                                   HospitalRepository hospitalRepository,
                                   QuotaRepository quotaRepository,
                                   UploadSessionRepository sessionRepository,
                                   ArtifactRepository artifactRepository) {
        this.registerHospital = registerHospital;
        this.updateHospital = updateHospital;
        this.hospitalRepository = hospitalRepository;
        this.quotaRepository = quotaRepository;
        this.sessionRepository = sessionRepository;
        this.artifactRepository = artifactRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HospitalResponse register(@Valid @RequestBody RegisterHospitalRequest request) {
        Hospital hospital = registerHospital.register(new RegisterHospitalUseCase.Command(
                request.cocode(),
                request.name(),
                request.licenseStartAt(),
                request.licenseEndAt(),
                request.maxStorageBytes()
        ));
        return HospitalResponse.from(hospital);
    }

    @GetMapping
    public List<HospitalResponse> list() {
        return hospitalRepository.findAll().stream()
                .map(HospitalResponse::from)
                .toList();
    }

    @GetMapping("/{cocode}")
    public HospitalResponse get(@PathVariable long cocode) {
        return hospitalRepository.findById(new HospitalId(cocode))
                .map(HospitalResponse::from)
                .orElseThrow(() -> new HospitalNotFoundException(new HospitalId(cocode)));
    }

    @PatchMapping("/{cocode}")
    public HospitalResponse update(@PathVariable long cocode,
                                   @RequestBody UpdateHospitalRequest request) {
        Hospital hospital = updateHospital.update(
                new HospitalId(cocode),
                new UpdateHospitalUseCase.Command(
                        request.name(),
                        request.licenseStartAt(),
                        request.licenseEndAt(),
                        request.maxStorageBytes(),
                        request.active()
                )
        );
        return HospitalResponse.from(hospital);
    }

    @GetMapping("/{cocode}/quota")
    public QuotaResponse quota(@PathVariable long cocode) {
        return quotaRepository.findByHospitalId(new HospitalId(cocode))
                .map(QuotaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Quota not found for cocode=" + cocode));
    }

    @GetMapping("/{cocode}/sessions")
    public List<SessionSummary> sessions(
            @PathVariable long cocode,
            @RequestParam(required = false) String status) {
        return sessionRepository.findByHospitalId(new HospitalId(cocode)).stream()
                .filter(s -> status == null || s.status().name().equalsIgnoreCase(status))
                .map(SessionSummary::from)
                .toList();
    }

    @GetMapping("/{cocode}/artifacts")
    public List<ArtifactSummary> artifacts(@PathVariable long cocode) {
        return artifactRepository.findByHospitalId(new HospitalId(cocode)).stream()
                .map(ArtifactSummary::from)
                .toList();
    }
}
