package com.worksphere.auth.service;

import com.worksphere.auth.domain.RefreshToken;
import com.worksphere.auth.repository.RefreshTokenRepository;
import com.worksphere.auth.security.IssuedRefreshToken;
import com.worksphere.auth.security.JwtService;
import com.worksphere.common.exception.AuthenticationInvalidException;
import com.worksphere.config.RefreshTokenProperties;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens. Every refresh replaces the presented token with a new one.
 * Presenting an already-rotated token again (outside a short grace period) is treated as theft, and all of
 * that user's sessions are ended.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository repository, JwtService jwtService, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(UserAccount user) {
        String rawToken = newRawToken();
        Instant expiresAt = clock.instant().plus(properties.ttl());
        repository.save(RefreshToken.issue(user, hash(rawToken), expiresAt));
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /** Exchanges a valid refresh token for a new access token and a new refresh token. */
    @Transactional(noRollbackFor = AuthenticationInvalidException.class)
    public AuthTokens refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationInvalidException();
        }
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(AuthenticationInvalidException::new);

        if (current.isRevoked()) {
            if (current.getRevokedAt().plus(properties.reuseGracePeriod()).isBefore(now)) {
                UUID userId = current.getUser().getId();
                LOGGER.warn("Revoked refresh token reused for user {}; ending all of their sessions", userId);
                repository.revokeAllActiveForUser(userId, now);
            }
            throw new AuthenticationInvalidException();
        }
        current.revoke(now);
        if (current.isExpired(now) || current.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationInvalidException();
        }

        UserAccount user = current.getUser();
        return new AuthTokens(jwtService.createAccessToken(user), issue(user));
    }

    /** Logout: revokes the given token if it exists. Unknown or empty tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    /** Ends every session of a user, e.g. when they are deactivated. */
    @Transactional
    public void revokeAllFor(UUID userId) {
        repository.revokeAllActiveForUser(userId, clock.instant());
    }

    /** Daily housekeeping: expired tokens are useless, so delete them. */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant());
        if (deleted > 0) {
            LOGGER.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
