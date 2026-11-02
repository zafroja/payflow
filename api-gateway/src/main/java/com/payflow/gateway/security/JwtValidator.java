package com.payflow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Verifies tokens issued by auth-service. Both sides share the same HMAC secret
 * (injected from a Kubernetes Secret / env var, never hard-coded in production).
 */
@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(@Value("${security.jwt.secret}") String base64Secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }

    /** Throws io.jsonwebtoken.JwtException if the token is invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
