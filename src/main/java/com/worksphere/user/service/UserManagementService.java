package com.worksphere.user.service;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.ConflictException;
import com.worksphere.common.exception.DuplicateResourceException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.tenant.repository.TenantRepository;
import com.worksphere.user.api.CreateUserRequest;
import com.worksphere.user.api.UserResponse;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

    private static final String NOT_FOUND = "User not found";

    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(JwtPrincipal principal, CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email is already in use");
        }
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        UserAccount user = UserAccount.createActiveMember(
                tenant,
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()));
        return UserResponse.from(userAccountRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(JwtPrincipal principal) {
        return userAccountRepository.findAllByTenant_IdOrderByCreatedAtAsc(principal.tenantId()).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(JwtPrincipal principal, UUID userId) {
        return UserResponse.from(find(principal, userId));
    }

    /** Idempotent. An admin cannot deactivate their own account. */
    @Transactional
    public UserResponse deactivate(JwtPrincipal principal, UUID userId) {
        if (principal.userId().equals(userId)) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        UserAccount user = find(principal, userId);
        user.deactivate();
        return UserResponse.from(userAccountRepository.saveAndFlush(user));
    }

    private UserAccount find(JwtPrincipal principal, UUID userId) {
        return userAccountRepository.findByIdAndTenant_Id(userId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}