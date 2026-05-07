package com.intocns.backup.infrastructure.security;

import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.TokenIssuer;
import com.intocns.backup.domain.port.TokenParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService implements TokenIssuer, TokenParser {

    private final SecretKey secretKey;
    private final long ttlMillis;

    public JwtService(
            @Value("${backup.auth.jwt-secret}") String secret,
            @Value("${backup.auth.token-ttl-minutes:15}") long ttlMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60 * 1_000;
    }

    @Override
    public String issue(HospitalId hospitalId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(hospitalId.cocode()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public HospitalId parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new HospitalId(Long.parseLong(claims.getSubject()));
    }
}
