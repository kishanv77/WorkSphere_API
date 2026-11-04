package com.worksphere.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.ConflictException;
import com.worksphere.common.exception.DuplicateResourceException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.tenant.repository.TenantRepository;
import com.worksphere.user.api.CreateUserRequest;
import com.worksphere.user.api.UserResponse;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserRole;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import com.worksphere.user.service.UserManagementService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserManagementService service;
    private Tenant tenant;
    private UserAccount admin;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(userAccountRepository, tenantRepository, passwordEncoder);
        tenant = Tenant.createActiveFree("Acme", "acme");
        admin = UserAccount.createActiveTenantAdmin(tenant, "Ada", "Admin", "ada@acme.test", "hash");
        principal = new JwtPrincipal(admin.getId(), tenant.getId(), UserRole.TENANT_ADMIN);
    }

    @Test
    void createMakesAnActiveMemberInTheCallersTenant() {
        when(userAccountRepository.existsByEmailIgnoreCase("bo@acme.test")).thenReturn(false);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(passwordEncoder.encode("a-long-password-1")).thenReturn("encoded");
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = service.create(principal,
                new CreateUserRequest(" Bo ", "Member", " Bo@Acme.Test ", "a-long-password-1"));

        assertEquals("bo@acme.test", response.email());
        assertEquals(UserRole.MEMBER, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
    }

    @Test
    void createWithTakenEmailIsConflict() {
        when(userAccountRepository.existsByEmailIgnoreCase("ada@acme.test")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(principal,
                new CreateUserRequest("A", "B", "ada@acme.test", "a-long-password-1")));
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void listIsTenantScoped() {
        when(userAccountRepository.findAllByTenant_Id(eq(tenant.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(admin)));

        assertEquals(1, service.list(principal, 0, 20).getTotalElements());
    }

    @Test
    void crossTenantUserIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(userAccountRepository.findByIdAndTenant_Id(foreign, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(principal, foreign));
        assertThrows(ResourceNotFoundException.class, () -> service.deactivate(principal, foreign));
    }

    @Test
    void deactivateWorksAndIsIdempotent() {
        UserAccount member = UserAccount.createActiveMember(tenant, "Bo", "M", "bo@acme.test", "hash");
        when(userAccountRepository.findByIdAndTenant_Id(member.getId(), tenant.getId()))
                .thenReturn(Optional.of(member));
        when(userAccountRepository.saveAndFlush(member)).thenReturn(member);

        assertEquals(UserStatus.INACTIVE, service.deactivate(principal, member.getId()).status());
        assertEquals(UserStatus.INACTIVE, service.deactivate(principal, member.getId()).status());
        assertNotEquals(admin.getId(), member.getId());
    }

    @Test
    void adminCannotDeactivateSelf() {
        assertThrows(ConflictException.class, () -> service.deactivate(principal, admin.getId()));
        verify(userAccountRepository, never()).saveAndFlush(any());
    }
}