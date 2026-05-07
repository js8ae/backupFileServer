package com.intocns.backup.api.admin;

import com.intocns.backup.api.admin.dto.HospitalResponse;
import com.intocns.backup.api.admin.dto.RegisterHospitalRequest;
import com.intocns.backup.api.admin.dto.UpdateHospitalRequest;
import com.intocns.backup.application.RegisterHospitalUseCase;
import com.intocns.backup.application.UpdateHospitalUseCase;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.exception.HospitalNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hospitals")
public class AdminHospitalController {

    private final RegisterHospitalUseCase registerHospital;
    private final UpdateHospitalUseCase updateHospital;
    private final HospitalRepository hospitalRepository;

    public AdminHospitalController(RegisterHospitalUseCase registerHospital,
                                   UpdateHospitalUseCase updateHospital,
                                   HospitalRepository hospitalRepository) {
        this.registerHospital = registerHospital;
        this.updateHospital = updateHospital;
        this.hospitalRepository = hospitalRepository;
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
}
