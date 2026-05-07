package com.intocns.backup.domain.port;

public interface PasswordHasher {
    String hash(String raw);
}
