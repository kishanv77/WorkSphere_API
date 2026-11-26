package com.worksphere.auth.api;

import com.worksphere.auth.security.IssuedRefreshToken;
import com.worksphere.config.RefreshTokenProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the refresh token cookie: httpOnly (unreadable by scripts), SameSite=Strict (not sent on cross-site
 * requests, which also blocks CSRF on the cookie-authenticated endpoints) and scoped to the auth endpoints only.
 */
@Component
public class RefreshTokenCookies {

    static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenCookies(RefreshTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String name() {
        return properties.cookieName();
    }

    public String create(IssuedRefreshToken token) {
        return base(token.token())
                .maxAge(Duration.between(clock.instant(), token.expiresAt()))
                .build()
                .toString();
    }

    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
