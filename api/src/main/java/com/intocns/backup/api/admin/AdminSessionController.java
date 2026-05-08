package com.intocns.backup.api.admin;

import com.intocns.backup.application.AbortUploadUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Tag(name = "Admin - Session", description = "업로드 세션 강제 제어 (X-Admin-Key 필요)")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/sessions")
public class AdminSessionController {

    private final AbortUploadUseCase abortUpload;

    public AdminSessionController(AbortUploadUseCase abortUpload) {
        this.abortUpload = abortUpload;
    }

    @Operation(
        summary = "업로드 세션 강제 중단",
        description = "진행 중인 세션을 ABORTED 상태로 전환하고 TUS 임시 파일을 삭제합니다."
    )
    @ApiResponse(responseCode = "204", description = "중단 성공")
    @ApiResponse(responseCode = "404", description = "세션 없음 (code: 1003)")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceAbort(
            @Parameter(description = "세션 UUID") @PathVariable UUID sessionId) throws IOException {
        abortUpload.forceAbort(sessionId);
    }
}
