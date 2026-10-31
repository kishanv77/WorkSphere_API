package com.worksphere.auth.security;

import com.worksphere.config.JwtProperties;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MINIMUM_HS256_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_HS256_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HMAC signing");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.jwtProperties = jwtProperties;
    }

    public IssuedAccessToken createAccessToken(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("userId", user.getId().toString())
                .claim("tenantId", user.getTenant().getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.get("userId", String.class));
        if (!userId.toString().equals(claims.getSubject())) {
            throw new JwtException("JWT subject does not match its user ID claim");
        }
        UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new JwtPrincipal(userId, tenantId, role);
    }
}
