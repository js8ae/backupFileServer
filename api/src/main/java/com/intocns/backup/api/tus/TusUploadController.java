package com.intocns.backup.api.tus;

import com.intocns.backup.application.AbortUploadUseCase;
import com.intocns.backup.application.HandlePatchUseCase;
import com.intocns.backup.application.InitiateUploadUseCase;
import com.intocns.backup.application.LinkTusUploadUseCase;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Tag(name = "Upload (TUS 1.0)", description = "TUS 1.0 프로토콜 기반 청크 업로드. 모든 요청에 `Authorization: Bearer <JWT>` 와 `Tus-Resumable: 1.0.0` 헤더가 필요합니다.")
@RestController
@RequestMapping("/files")
public class TusUploadController {

    private final ChunkedUploadProtocol protocol;
    private final InitiateUploadUseCase initiateUpload;
    private final LinkTusUploadUseCase linkTusUpload;
    private final HandlePatchUseCase handlePatch;
    private final AbortUploadUseCase abortUpload;

    public TusUploadController(
            ChunkedUploadProtocol protocol,
            InitiateUploadUseCase initiateUpload,
            LinkTusUploadUseCase linkTusUpload,
            HandlePatchUseCase handlePatch,
            AbortUploadUseCase abortUpload) {
        this.protocol = protocol;
        this.initiateUpload = initiateUpload;
        this.linkTusUpload = linkTusUpload;
        this.handlePatch = handlePatch;
        this.abortUpload = abortUpload;
    }

    @Operation(
        summary = "업로드 세션 생성 (TUS POST)",
        description = "업로드 세션을 생성하고 TUS 업로드 리소스 URI 를 반환합니다."
    )
    @ApiResponse(
        responseCode = "201",
        description = "세션 생성 성공",
        headers = {
            @Header(name = "Location", description = "PATCH 요청에 사용할 TUS URI (예: /files/{tusId})"),
            @Header(name = "X-Session-Id", description = "내부 세션 UUID")
        }
    )
    @ApiResponse(responseCode = "403", description = "라이선스 만료 (code: 1006)")
    @ApiResponse(responseCode = "507", description = "쿼터 초과 (code: 1005)")
    @PostMapping
    public ResponseEntity<Void> post(
            @Parameter(description = "백업 유형 (DB | FILE)", required = true)
            @RequestHeader("Upload-Type") String uploadType,
            @Parameter(description = "원본 파일명", required = true)
            @RequestHeader("Upload-Filename") String filename,
            @Parameter(description = "전체 파일 크기 (bytes)", required = true)
            @RequestHeader("Upload-Length") long uploadLength,
            @Parameter(description = "SHA-256 hex (선택 — 전달 시 완료 후 무결성 검증)")
            @RequestHeader(value = "Upload-Sha256", required = false) String sha256,
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        BackupType type = BackupType.valueOf(uploadType.toUpperCase());
        UUID sessionId = initiateUpload.initiate(new InitiateUploadUseCase.Command(
                caller, type, filename, uploadLength, sha256));

        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));

        String tusLocation = response.getHeader("Location");
        if (tusLocation != null) {
            linkTusUpload.link(sessionId, tusLocation, caller);
        }

        response.setHeader("X-Session-Id", sessionId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create(tusLocation != null ? tusLocation : "")).build();
    }

    @Operation(
        summary = "청크 전송 (TUS PATCH)",
        description = "파일 데이터를 청크 단위로 전송합니다. 마지막 청크 전송 시 SHA-256 검증 후 artifact 로 승격됩니다."
    )
    @ApiResponse(responseCode = "204", description = "청크 수신 성공")
    @ApiResponse(responseCode = "422", description = "SHA-256 불일치 (code: 1007)")
    @PatchMapping("/**")
    public void patch(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String tusUploadUri = extractTusUploadUri(request);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
        try {
            handlePatch.handle(tusUploadUri, caller);
        } catch (IOException | RuntimeException e) {
            if (!response.isCommitted()) {
                response.reset();
            }
            throw e;
        }
    }

    @Operation(
        summary = "업로드 offset 조회 (TUS HEAD)",
        description = "현재까지 수신된 바이트 수를 `Upload-Offset` 헤더로 반환합니다. Resume 시 이 값부터 PATCH 를 재개합니다."
    )
    @ApiResponse(responseCode = "204", description = "조회 성공 — Upload-Offset 헤더 확인")
    @RequestMapping(value = "/**", method = RequestMethod.HEAD)
    public void head(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        handlePatch.verifyAccess(extractTusUploadUri(request), caller);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @Operation(summary = "TUS 서버 기능 조회 (TUS OPTIONS)")
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public void options(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @Operation(
        summary = "업로드 중단 (TUS DELETE)",
        description = "업로드를 ABORTED 상태로 전환하고 TUS 임시 파일을 삭제합니다."
    )
    @ApiResponse(responseCode = "204", description = "중단 성공")
    @DeleteMapping("/**")
    public void delete(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String tusUploadUri = extractTusUploadUri(request);
        abortUpload.abortByTusUri(tusUploadUri, caller);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    @Operation(summary = "파일 다운로드 (TUS GET)")
    @GetMapping("/**")
    public void get(
            @AuthenticationPrincipal HospitalId caller,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        handlePatch.verifyAccess(extractTusUploadUri(request), caller);
        protocol.process(new JakartaHttpRequestWrapper(request), new JakartaHttpResponseWrapper(response));
    }

    private String extractTusUploadUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx.isEmpty() ? uri : uri.substring(ctx.length());
    }
}
