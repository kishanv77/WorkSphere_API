package com.worksphere.user.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.user.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.create(principal, request));
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return userManagementService.list(principal);
    }

    @GetMapping("/{userId}")
    public UserResponse get(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable UUID userId) {
        return userManagementService.get(principal, userId);
    }

    @PatchMapping("/{userId}/deactivate")
    public UserResponse deactivate(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable UUID userId) {
        return userManagementService.deactivate(principal, userId);
    }
}