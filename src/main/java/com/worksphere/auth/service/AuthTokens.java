package com.worksphere.auth.service;

import com.worksphere.auth.api.LoginResponse;
import com.worksphere.auth.security.IssuedAccessToken;
import com.worksphere.auth.security.IssuedRefreshToken;

/** The access token (returned in the body) and refresh token (set as a cookie) from a login or refresh. */
public record AuthTokens(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {

    public LoginResponse toResponse() {
        return new LoginResponse(accessToken.token(), "Bearer", accessToken.expiresAt());
    }
}
