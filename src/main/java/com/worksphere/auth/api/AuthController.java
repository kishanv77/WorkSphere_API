package com.worksphere.auth.api;

import com.worksphere.auth.service.TenantRegistrationService;
import com.worksphere.auth.service.CurrentUserService;
import com.worksphere.auth.service.LoginService;
import com.worksphere.auth.security.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TenantRegistrationService tenantRegistrationService;
    private final LoginService loginService;
    private final CurrentUserService currentUserService;

    public AuthController(
            TenantRegistrationService tenantRegistrationService,
            LoginService loginService,
            CurrentUserService currentUserService) {
        this.tenantRegistrationService = tenantRegistrationService;
        this.loginService = loginService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register-tenant")
    public ResponseEntity<TenantRegistrationResponse> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request) {
        TenantRegistrationResponse response = tenantRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return currentUserService.getCurrentUser(principal);
    }
}
