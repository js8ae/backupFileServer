package com.intocns.backup.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {
}
