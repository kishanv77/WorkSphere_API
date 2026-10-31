package com.worksphere.auth.service;

import com.worksphere.auth.api.RegisterTenantRequest;
import com.worksphere.auth.api.TenantRegistrationResponse;
import com.worksphere.common.exception.DuplicateResourceException;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.tenant.repository.TenantRepository;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantRegistrationService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantRegistrationResponse register(RegisterTenantRequest request) {
        String tenantSlug = request.tenantSlug().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (tenantRepository.existsBySlugIgnoreCase(tenantSlug)) {
            throw new DuplicateResourceException("Tenant slug is already in use");
        }

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email is already in use");
        }

        Tenant tenant = Tenant.createActiveFree(request.tenantName().trim(), tenantSlug);
        UserAccount user = UserAccount.createActiveTenantAdmin(
                tenant,
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()));

        tenantRepository.save(tenant);
        userAccountRepository.save(user);

        return new TenantRegistrationResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                user.getId(),
                user.getEmail(),
                user.getRole());
    }
}
