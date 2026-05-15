package com.intocns.backup.api.admin;

import com.intocns.backup.application.RestoreArtifactUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Tag(name = "Admin - Artifact", description = "백업 아티팩트 관리 (X-Admin-Key 필요)")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/artifacts")
public class AdminArtifactController {

    private final RestoreArtifactUseCase restoreArtifact;

    public AdminArtifactController(RestoreArtifactUseCase restoreArtifact) {
        this.restoreArtifact = restoreArtifact;
    }

    @Operation(
        summary = "Trash에서 아티팩트 복구",
        description = "purged_at이 설정된 아티팩트를 trash에서 artifacts 경로로 복원하고 쿼터를 복구합니다."
    )
    @ApiResponse(responseCode = "204", description = "복구 성공")
    @ApiResponse(responseCode = "404", description = "아티팩트 없음 (code: 1011)")
    @ApiResponse(responseCode = "409", description = "아티팩트가 trash 상태가 아님 (code: 1012)")
    @PostMapping("/{artifactId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(
            @Parameter(description = "아티팩트 UUID") @PathVariable UUID artifactId) throws IOException {
        restoreArtifact.restore(artifactId);
    }
}
