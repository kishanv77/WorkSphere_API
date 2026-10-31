package com.worksphere.auth.service;

import com.worksphere.auth.api.CurrentUserResponse;
import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.AuthenticationInvalidException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(JwtPrincipal principal) {
        UserAccount user = userAccountRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));

        if (user.getStatus() != UserStatus.ACTIVE || !user.getTenant().getId().equals(principal.tenantId())) {
            throw new AuthenticationInvalidException();
        }

        return new CurrentUserResponse(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole());
    }
}
