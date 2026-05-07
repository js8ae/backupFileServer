package com.intocns.backup.domain.model;

import java.time.Instant;

public record CredentialInfo(String clientId, Instant createdAt) {
}
