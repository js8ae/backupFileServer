package com.intocns.backup.api.auth;

import com.intocns.backup.api.auth.dto.TokenRequest;
import com.intocns.backup.api.auth.dto.TokenResponse;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.CredentialAuthenticator;
import com.intocns.backup.domain.port.TokenIssuer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CredentialAuthenticator authenticator;
    private final TokenIssuer tokenIssuer;

    public AuthController(CredentialAuthenticator authenticator, TokenIssuer tokenIssuer) {
        this.authenticator = authenticator;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        HospitalId hospitalId = authenticator.authenticate(request.clientId(), request.clientSecret())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return TokenResponse.bearer(tokenIssuer.issue(hospitalId));
    }
}
