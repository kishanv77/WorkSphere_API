package com.worksphere.auth.service;

import com.worksphere.auth.api.LoginRequest;
import com.worksphere.auth.api.LoginResponse;
import com.worksphere.auth.security.IssuedAccessToken;
import com.worksphere.auth.security.JwtService;
import com.worksphere.common.exception.InvalidCredentialsException;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(
            UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        IssuedAccessToken accessToken = jwtService.createAccessToken(user);
        return new LoginResponse(accessToken.token(), "Bearer", accessToken.expiresAt());
    }
}
