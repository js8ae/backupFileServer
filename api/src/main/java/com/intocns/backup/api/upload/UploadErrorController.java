package com.intocns.backup.api.upload;

import com.intocns.backup.api.upload.dto.ReportClientErrorRequest;
import com.intocns.backup.application.ReportClientErrorUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Upload", description = "업로드 관련 API")
@RestController
@RequestMapping("/upload/sessions")
public class UploadErrorController {

    private final ReportClientErrorUseCase reportClientError;

    public UploadErrorController(ReportClientErrorUseCase reportClientError) {
        this.reportClientError = reportClientError;
    }

    @Operation(summary = "클라이언트 업로드 에러 리포팅", description = "업로드 중 발생한 클라이언트 측 에러를 서버에 기록합니다. JWT 인증 불필요.")
    @PostMapping("/{sessionId}/errors")
    @ResponseStatus(HttpStatus.CREATED)
    public void reportError(
            @Parameter(description = "업로드 세션 ID") @PathVariable UUID sessionId,
            @RequestBody @Valid ReportClientErrorRequest request) {
        reportClientError.report(
                sessionId,
                request.errorType(),
                request.errorMessage(),
                request.byteOffset(),
                request.clientInfo(),
                request.occurredAt()
        );
    }
}
