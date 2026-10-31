package com.worksphere.auth.api;

import com.worksphere.user.domain.UserRole;
import java.util.UUID;

public record TenantRegistrationResponse(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        UUID userId,
        String email,
        UserRole role) {
}
