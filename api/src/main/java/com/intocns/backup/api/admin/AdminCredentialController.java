package com.intocns.backup.api.admin;

import com.intocns.backup.api.admin.dto.CredentialSummary;
import com.intocns.backup.api.admin.dto.IssuedCredentialResponse;
import com.intocns.backup.application.IssueCredentialUseCase;
import com.intocns.backup.application.ListCredentialsUseCase;
import com.intocns.backup.application.RevokeCredentialUseCase;
import com.intocns.backup.domain.model.HospitalId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Credential", description = "병원 Credential 발급·폐기 (X-Admin-Key 필요)")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/hospitals/{cocode}/credentials")
public class AdminCredentialController {

    private final IssueCredentialUseCase issueCredential;
    private final ListCredentialsUseCase listCredentials;
    private final RevokeCredentialUseCase revokeCredential;

    public AdminCredentialController(IssueCredentialUseCase issueCredential,
                                     ListCredentialsUseCase listCredentials,
                                     RevokeCredentialUseCase revokeCredential) {
        this.issueCredential = issueCredential;
        this.listCredentials = listCredentials;
        this.revokeCredential = revokeCredential;
    }

    @Operation(
        summary = "Credential 발급",
        description = "clientId + clientSecret 쌍을 발급합니다. clientSecret 은 이 응답에서만 평문으로 노출됩니다."
    )
    @ApiResponse(responseCode = "201", description = "발급 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedCredentialResponse issue(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        IssueCredentialUseCase.IssuedCredential issued = issueCredential.issue(new HospitalId(cocode));
        return new IssuedCredentialResponse(issued.clientId(), issued.clientSecret());
    }

    @Operation(summary = "Credential 목록 조회", description = "폐기된 Credential 포함 전체 목록을 반환합니다.")
    @GetMapping
    public List<CredentialSummary> list(
            @Parameter(description = "병원 코드") @PathVariable long cocode) {
        return listCredentials.list(new HospitalId(cocode)).stream()
                .map(c -> new CredentialSummary(c.clientId(), c.createdAt()))
                .toList();
    }

    @Operation(
        summary = "Credential 폐기",
        description = "해당 clientId 를 소프트 삭제합니다. 이후 해당 Credential 로 발급된 JWT 는 인증 실패합니다."
    )
    @ApiResponse(responseCode = "204", description = "폐기 성공")
    @ApiResponse(responseCode = "404", description = "Credential 없음 (code: 1002)")
    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @Parameter(description = "병원 코드") @PathVariable long cocode,
            @Parameter(description = "폐기할 clientId") @PathVariable String clientId) {
        revokeCredential.revoke(new HospitalId(cocode), clientId);
    }
}
