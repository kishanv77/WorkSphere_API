package com.worksphere.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtAuthenticationFilter;
import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.auth.security.JwtService;
import com.worksphere.common.security.RestAuthenticationEntryPoint;
import com.worksphere.user.domain.UserRole;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    @Mock
    private JwtService jwtService;

    @Mock
    private RestAuthenticationEntryPoint entryPoint;

    @Mock
    private UserAccountRepository userAccountRepository;

    private JwtAuthenticationFilter filter;
    private JwtPrincipal principal;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, entryPoint, userAccountRepository);
        principal = new JwtPrincipal(UUID.randomUUID(), UUID.randomUUID(), UserRole.MEMBER);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUserIsAuthenticated() throws Exception {
        when(jwtService.parseAccessToken("token")).thenReturn(principal);
        when(userAccountRepository.existsByIdAndTenant_IdAndStatus(
                principal.userId(), principal.tenantId(), UserStatus.ACTIVE)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void deactivatedUserIsRejectedEvenWithAValidToken() throws Exception {
        when(jwtService.parseAccessToken("token")).thenReturn(principal);
        when(userAccountRepository.existsByIdAndTenant_IdAndStatus(
                principal.userId(), principal.tenantId(), UserStatus.ACTIVE)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        verify(entryPoint).commence(any(), any(), any(BadCredentialsException.class));
    }
}