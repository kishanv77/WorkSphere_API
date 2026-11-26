package com.worksphere.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import com.worksphere.auth.domain.RefreshToken;
import com.worksphere.auth.repository.RefreshTokenRepository;
import com.worksphere.auth.security.IssuedAccessToken;
import com.worksphere.auth.security.IssuedRefreshToken;
import com.worksphere.auth.security.JwtService;
import com.worksphere.auth.service.AuthTokens;
import com.worksphere.auth.service.RefreshTokenService;
import com.worksphere.common.exception.AuthenticationInvalidException;
import com.worksphere.config.RefreshTokenProperties;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.user.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private JwtService jwtService;

    private final MutableClock clock = new MutableClock(START);
    private final Map<String, RefreshToken> stored = new HashMap<>();
    private RefreshTokenService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        RefreshTokenProperties properties =
                new RefreshTokenProperties(Duration.ofDays(7), Duration.ofSeconds(30), "ws_refresh", true);
        service = new RefreshTokenService(repository, jwtService, properties, clock);
        user = UserAccount.createActiveMember(
                Tenant.createActiveFree("Acme", "acme"), "Bo", "M", "bo@acme.test", "hash");
        // Behave like a tiny in-memory table keyed by token hash.
        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            stored.put(token.getTokenHash(), token);
            return token;
        });
        lenient().when(repository.findByTokenHash(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<String>getArgument(0))));
        lenient().when(jwtService.createAccessToken(user))
                .thenReturn(new IssuedAccessToken("access", START.plusSeconds(900)));
    }

    @Test
    void issueStoresOnlyAHashOfTheToken() {
        IssuedRefreshToken issued = service.issue(user);

        assertEquals(1, stored.size());
        RefreshToken saved = stored.values().iterator().next();
        assertNotEquals(issued.token(), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertEquals(START.plus(Duration.ofDays(7)), issued.expiresAt());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAnAccessToken() {
        IssuedRefreshToken first = service.issue(user);

        AuthTokens tokens = service.refresh(first.token());

        assertEquals("access", tokens.accessToken().token());
        assertNotEquals(first.token(), tokens.refreshToken().token());
        assertEquals(2, stored.size());
        assertEquals(1, stored.values().stream().filter(token -> token.isRevoked()).count());
    }

    @Test
    void reuseWithinTheGracePeriodIsRejectedWithoutEndingOtherSessions() {
        IssuedRefreshToken first = service.issue(user);
        service.refresh(first.token());
        clock.advance(Duration.ofSeconds(5));

        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(first.token()));
        verify(repository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void reuseAfterTheGracePeriodEndsAllOfTheUsersSessions() {
        IssuedRefreshToken first = service.issue(user);
        service.refresh(first.token());
        clock.advance(Duration.ofMinutes(5));

        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(first.token()));
        verify(repository).revokeAllActiveForUser(user.getId(), clock.instant());
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedRefreshToken first = service.issue(user);
        clock.advance(Duration.ofDays(8));

        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(first.token()));
    }

    @Test
    void deactivatedUserCannotRefresh() {
        IssuedRefreshToken first = service.issue(user);
        user.deactivate();

        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(first.token()));
        assertTrue(stored.values().iterator().next().isRevoked());
    }

    @Test
    void unknownOrMissingTokensAreRejected() {
        assertThrows(AuthenticationInvalidException.class, () -> service.refresh("not-a-real-token"));
        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(null));
    }

    @Test
    void logoutRevokesTheToken() {
        IssuedRefreshToken first = service.issue(user);

        service.revoke(first.token());
        service.revoke(null);

        assertTrue(stored.values().iterator().next().isRevoked());
        assertThrows(AuthenticationInvalidException.class, () -> service.refresh(first.token()));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
