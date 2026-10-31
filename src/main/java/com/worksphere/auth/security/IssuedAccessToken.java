package com.worksphere.auth.security;

import java.time.Instant;

public record IssuedAccessToken(String token, Instant expiresAt) {
}
