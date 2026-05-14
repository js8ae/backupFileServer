package com.intocns.backup.api.auth;

import com.intocns.backup.api.auth.dto.TokenRequest;
import com.intocns.backup.api.auth.dto.TokenResponse;
import com.intocns.backup.domain.model.AuthAuditLog;
import com.intocns.backup.domain.model.AuthAuditResult;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.AuthAuditPort;
import com.intocns.backup.domain.port.CredentialAuthenticator;
import com.intocns.backup.domain.port.TokenIssuer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Auth", description = "병원 인증 및 JWT 발급")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CredentialAuthenticator authenticator;
    private final TokenIssuer tokenIssuer;
    private final AuthAuditPort authAuditPort;

    public AuthController(CredentialAuthenticator authenticator, TokenIssuer tokenIssuer,
                          AuthAuditPort authAuditPort) {
        this.authenticator = authenticator;
        this.tokenIssuer = tokenIssuer;
        this.authAuditPort = authAuditPort;
    }

    @Operation(
        summary = "JWT 발급",
        description = "clientId + clientSecret 으로 인증 후 JWT(TTL 15분)를 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "발급 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패 — clientId/clientSecret 불일치")
    @SecurityRequirements
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        Instant now = Instant.now();
        Optional<HospitalId> hospitalId = authenticator.authenticate(request.clientId(), request.clientSecret());
        if (hospitalId.isEmpty()) {
            authAuditPort.record(new AuthAuditLog(
                UUID.randomUUID(), request.clientId(), null, clientIp, AuthAuditResult.FAILED, now));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        HospitalId id = hospitalId.get();
        authAuditPort.record(new AuthAuditLog(
            UUID.randomUUID(), request.clientId(), id, clientIp, AuthAuditResult.SUCCESS, now));
        return TokenResponse.bearer(tokenIssuer.issue(id));
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
