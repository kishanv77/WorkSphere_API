package com.worksphere.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Refresh token settings.
 *
 * @param ttl how long a refresh token (and so a browser session) lasts without use
 * @param reuseGracePeriod a just-rotated token presented again within this window is rejected
 *     without revoking the user's other sessions (covers two tabs refreshing at once)
 * @param cookieName name of the httpOnly cookie that carries the token
 * @param cookieSecure send the cookie only over HTTPS (browsers also allow it on http://localhost)
 */
@Validated
@ConfigurationProperties(prefix = "app.security.refresh")
public record RefreshTokenProperties(
        @NotNull @DefaultValue("P7D") Duration ttl,
        @NotNull @DefaultValue("PT30S") Duration reuseGracePeriod,
        @NotBlank @DefaultValue("ws_refresh") String cookieName,
        @DefaultValue("true") boolean cookieSecure) {
}
