package com.intocns.backup.api.admin;

import com.intocns.backup.api.admin.dto.*;
import com.intocns.backup.application.RegisterHospitalUseCase;
import com.intocns.backup.application.ResetHospitalDataUseCase;
import com.intocns.backup.application.UpdateHospitalUseCase;
import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.model.Hospital;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.ArtifactRepository;
import com.intocns.backup.domain.port.HospitalRepository;
import com.intocns.backup.domain.port.QuotaRepository;
import com.intocns.backup.domain.port.UploadSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Admin - Hospital", description = "병원 관리 (X-Admin-Key 필요)")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/hospitals")
public class AdminHospitalController {

    private final RegisterHospitalUseCase registerHospital;
    private final UpdateHospitalUseCase updateHospital;
    private final ResetHospitalDataUseCase resetHospitalData;
    private final HospitalRepository hospitalRepository;
    private final QuotaRepository quotaRepository;
    private final UploadSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;

    public AdminHospitalController(RegisterHospitalUseCase registerHospital,
                                   UpdateHospitalUseCase updateHospital,
                                   ResetHospitalDataUseCase resetHospitalData,
                                   HospitalRepository hospitalRepository,
                                   QuotaRepository quotaRepository,
                                   UploadSessionRepository sessionRepository,
                                   ArtifactRepository artifactRepository) {
        this.registerHospital = registerHospital;
        this.updateHospital = updateHospital;
        this.resetHospitalData = resetHospitalData;
        this.hospitalRepository = hospitalRepository;
        this.quotaRepository = quotaRepository;
        this.sessionRepository = sessionRepository;
        this.artifactRepository = artifactRepository;
    }

    @Operation(summary = "병원 등록")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "409", description = "이미 등록된 병원 (code: 1000)")
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

    @Operation(summary = "병원 목록 조회")
    @GetMapping
    public List<HospitalResponse> list() {
        return hospitalRepository.findAll().stream()
                .map(HospitalResponse::from)
                .toList();
    }

    @Operation(summary = "병원 단건 조회")
    @ApiResponse(responseCode = "404", description = "병원 없음 (code: 1001)")
    @GetMapping("/{cocode}")
    public HospitalResponse get(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        return hospitalRepository.findById(new HospitalId(cocode))
                .map(HospitalResponse::from)
                .orElseThrow(() -> new HospitalNotFoundException(new HospitalId(cocode)));
    }

    @Operation(summary = "병원 정보 수정", description = "null 필드는 변경하지 않습니다.")
    @ApiResponse(responseCode = "404", description = "병원 없음 (code: 1001)")
    @PatchMapping("/{cocode}")
    public HospitalResponse update(
            @Parameter(description = "병원 코드") @PathVariable long cocode,
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

    @Operation(summary = "쿼터 조회", description = "병원별 스토리지 사용량 및 한도를 조회합니다.")
    @ApiResponse(responseCode = "404", description = "쿼터 정보 없음")
    @GetMapping("/{cocode}/quota")
    public QuotaResponse quota(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        return quotaRepository.findByHospitalId(new HospitalId(cocode))
                .map(QuotaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Quota not found for cocode=" + cocode));
    }

    @Operation(summary = "업로드 세션 목록 조회")
    @GetMapping("/{cocode}/sessions")
    public List<SessionSummary> sessions(
            @Parameter(description = "병원 코드") @PathVariable long cocode,
            @Parameter(description = "상태 필터 (INITIATED | UPLOADING | COMPLETED | ABORTED | EXPIRED)")
            @RequestParam(required = false) String status) {
        return sessionRepository.findByHospitalId(new HospitalId(cocode)).stream()
                .filter(s -> status == null || s.status().name().equalsIgnoreCase(status))
                .map(SessionSummary::from)
                .toList();
    }

    @Operation(summary = "백업 아티팩트 목록 조회", description = "완료된 백업 파일 목록을 반환합니다.")
    @GetMapping("/{cocode}/artifacts")
    public List<ArtifactSummary> artifacts(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        return artifactRepository.findByHospitalId(new HospitalId(cocode)).stream()
                .map(ArtifactSummary::from)
                .toList();
    }

    @Operation(summary = "백업 데이터 초기화",
               description = "해당 병원의 모든 백업 파일을 trash로 이동하고 쿼터를 초기화합니다. 진행 중인 업로드 세션도 함께 중단됩니다.")
    @ApiResponse(responseCode = "200", description = "초기화 성공")
    @DeleteMapping("/{cocode}/data")
    public ResetDataResponse resetData(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        return ResetDataResponse.from(resetHospitalData.reset(new HospitalId(cocode)));
    }
}
