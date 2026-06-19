package com.intocns.backup.api.hospital;

import com.intocns.backup.application.CheckHospitalStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Hospital", description = "병원 상태 조회")
@RestController
@RequestMapping("/hospitals")
public class HospitalController {

    private final CheckHospitalStatusUseCase checkHospitalStatus;

    public HospitalController(CheckHospitalStatusUseCase checkHospitalStatus) {
        this.checkHospitalStatus = checkHospitalStatus;
    }

    @Operation(
        summary = "병원 백업 사용 여부 조회",
        description = "cocode에 해당하는 병원이 백업 서비스를 사용 중인지(활성 + 라이선스 유효) 반환합니다. 인증 불필요."
    )
    @SecurityRequirements
    @GetMapping("/{cocode}/status")
    public HospitalStatusResponse status(@PathVariable long cocode) {
        return new HospitalStatusResponse(checkHospitalStatus.isEnabled(cocode));
    }

    public record HospitalStatusResponse(boolean isEnable) {}
}
