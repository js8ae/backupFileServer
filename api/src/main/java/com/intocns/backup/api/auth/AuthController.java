package com.intocns.backup.api.auth;

import com.intocns.backup.api.auth.dto.TokenRequest;
import com.intocns.backup.api.auth.dto.TokenResponse;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.CredentialAuthenticator;
import com.intocns.backup.domain.port.TokenIssuer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Auth", description = "병원 인증 및 JWT 발급")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CredentialAuthenticator authenticator;
    private final TokenIssuer tokenIssuer;

    public AuthController(CredentialAuthenticator authenticator, TokenIssuer tokenIssuer) {
        this.authenticator = authenticator;
        this.tokenIssuer = tokenIssuer;
    }

    @Operation(
        summary = "JWT 발급",
        description = "clientId + clientSecret 으로 인증 후 JWT(TTL 15분)를 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "발급 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패 — clientId/clientSecret 불일치")
    @SecurityRequirements
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        HospitalId hospitalId = authenticator.authenticate(request.clientId(), request.clientSecret())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return TokenResponse.bearer(tokenIssuer.issue(hospitalId));
    }
}
