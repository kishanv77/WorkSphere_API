package com.worksphere.auth.security;

import java.time.Instant;

/** A newly issued refresh token. {@code token} is the raw value and is never stored. */
public record IssuedRefreshToken(String token, Instant expiresAt) {
}
