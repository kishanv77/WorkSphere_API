package com.worksphere.auth.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.auth.service.AuthTokens;
import com.worksphere.auth.service.CurrentUserService;
import com.worksphere.auth.service.LoginService;
import com.worksphere.auth.service.RefreshTokenService;
import com.worksphere.auth.service.TenantRegistrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TenantRegistrationService tenantRegistrationService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserService currentUserService;
    private final RefreshTokenCookies cookies;

    public AuthController(
            TenantRegistrationService tenantRegistrationService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            CurrentUserService currentUserService,
            RefreshTokenCookies cookies) {
        this.tenantRegistrationService = tenantRegistrationService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.currentUserService = currentUserService;
        this.cookies = cookies;
    }

    @PostMapping("/register-tenant")
    public ResponseEntity<TenantRegistrationResponse> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request) {
        TenantRegistrationResponse response = tenantRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Returns a short-lived access token and sets a long-lived refresh token cookie. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(loginService.login(request, httpRequest.getRemoteAddr()));
    }

    /** Exchanges the refresh token cookie for a new access token; the cookie is rotated on every call. */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpRequest) {
        return withRefreshCookie(refreshTokenService.refresh(refreshCookie(httpRequest)));
    }

    /** Revokes the refresh token cookie (if any) and clears it. Always succeeds. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        refreshTokenService.revoke(refreshCookie(httpRequest));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return currentUserService.getCurrentUser(principal);
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken()))
                .body(tokens.toResponse());
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookies.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
