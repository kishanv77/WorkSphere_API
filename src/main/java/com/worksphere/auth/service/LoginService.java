package com.worksphere.auth.service;

import com.worksphere.auth.api.LoginRequest;
import com.worksphere.auth.security.JwtService;
import com.worksphere.auth.security.LoginAttemptLimiter;
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
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public LoginService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Transactional
    public AuthTokens login(LoginRequest request, String clientAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        loginAttemptLimiter.checkAllowed(email, clientAddress);

        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttemptLimiter.recordFailure(email, clientAddress);
            throw new InvalidCredentialsException();
        }

        loginAttemptLimiter.recordSuccess(email);
        return new AuthTokens(jwtService.createAccessToken(user), refreshTokenService.issue(user));
    }
}
