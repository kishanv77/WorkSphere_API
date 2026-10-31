package com.worksphere.auth.security;

import com.worksphere.user.domain.UserRole;
import java.util.UUID;

public record JwtPrincipal(UUID userId, UUID tenantId, UserRole role) {
}
