package com.worksphere.auth.security;

import com.worksphere.common.security.RestAuthenticationEntryPoint;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            authenticationEntryPoint.commence(
                    request, response, new BadCredentialsException("Bearer token is malformed"));
            return;
        }

        try {
            JwtPrincipal principal = jwtService.parseAccessToken(authorization.substring(BEARER_PREFIX.length()));
            if (!userAccountRepository.existsByIdAndTenant_IdAndStatus(
                    principal.userId(), principal.tenantId(), UserStatus.ACTIVE)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request, response, new BadCredentialsException("User is no longer active"));
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request, response, new BadCredentialsException("Bearer token is invalid", exception));
        }
    }
}
