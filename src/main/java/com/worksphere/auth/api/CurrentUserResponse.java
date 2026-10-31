package com.worksphere.auth.api;

import com.worksphere.user.domain.UserRole;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UserRole role) {
}
