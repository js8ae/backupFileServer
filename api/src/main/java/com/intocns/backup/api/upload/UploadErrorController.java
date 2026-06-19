package com.intocns.backup.api.upload;

import com.intocns.backup.api.upload.dto.ReportClientErrorRequest;
import com.intocns.backup.application.ReportClientErrorUseCase;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.TokenParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Upload", description = "업로드 관련 API")
@RestController
@RequestMapping("/upload")
public class UploadErrorController {

    private final ReportClientErrorUseCase reportClientError;
    private final TokenParser tokenParser;

    public UploadErrorController(ReportClientErrorUseCase reportClientError, TokenParser tokenParser) {
        this.reportClientError = reportClientError;
        this.tokenParser = tokenParser;
    }

    @Operation(summary = "클라이언트 업로드 에러 리포팅",
               description = "업로드 중 발생한 클라이언트 측 에러를 기록합니다. " +
                             "JWT·sessionId·uploadUri 모두 선택 사항이며, 있는 데이터만 기록합니다. " +
                             "uploadUri(TUS URI, 예: /files/{tusId})를 보내면 만료된 JWT 없이도 " +
                             "세션을 복원해 session_id·cocode 를 채웁니다.")
    @PostMapping("/errors")
    @ResponseStatus(HttpStatus.CREATED)
    public void reportError(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody @Valid ReportClientErrorRequest request) {
        HospitalId hospitalId = extractHospitalId(authHeader);
        reportClientError.report(
                request.sessionId(),
                request.uploadUri(),
                hospitalId,
                request.errorType(),
                request.errorMessage(),
                request.byteOffset(),
                request.clientInfo(),
                request.occurredAt()
        );
    }

    private HospitalId extractHospitalId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return tokenParser.parse(authHeader.substring(7));
        } catch (Exception ignored) {
            return null;
        }
    }
}
