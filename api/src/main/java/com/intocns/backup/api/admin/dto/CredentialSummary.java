package com.intocns.backup.api.admin.dto;

import java.time.Instant;

public record CredentialSummary(String clientId, Instant createdAt) {
}
