package com.worksphere.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.DuplicateResourceException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.project.api.CreateProjectRequest;
import com.worksphere.project.api.ProjectResponse;
import com.worksphere.project.api.UpdateProjectRequest;
import com.worksphere.project.domain.Project;
import com.worksphere.project.domain.ProjectStatus;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.project.service.ProjectService;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.tenant.repository.TenantRepository;
import com.worksphere.user.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TenantRepository tenantRepository;

    private ProjectService service;
    private Tenant tenant;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, tenantRepository);
        tenant = Tenant.createActiveFree("Acme", "acme");
        principal = new JwtPrincipal(UUID.randomUUID(), tenant.getId(), UserRole.TENANT_ADMIN);
    }

    @Test
    void createSucceedsForAuthenticatedTenant() {
        when(projectRepository.existsByTenant_IdAndNameIgnoreCase(tenant.getId(), "Alpha")).thenReturn(false);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        ProjectResponse response = service.create(principal, new CreateProjectRequest(" Alpha ", "desc"));

        assertEquals("Alpha", response.name());
        assertEquals(ProjectStatus.ACTIVE, response.status());
    }

    @Test
    void createDuplicateNameIsConflict() {
        when(projectRepository.existsByTenant_IdAndNameIgnoreCase(tenant.getId(), "Alpha")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(principal, new CreateProjectRequest("Alpha", null)));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void listReturnsOnlyCurrentTenantProjects() {
        Project mine = Project.createActive(tenant, "Mine", null);
        when(projectRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenant.getId())).thenReturn(List.of(mine));

        List<ProjectResponse> result = service.list(principal);

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).name());
    }

    @Test
    void crossTenantLookupIsNotFound() {
        UUID otherTenantProjectId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenant_Id(otherTenantProjectId, tenant.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(principal, otherTenantProjectId));
    }

    @Test
    void updateWorksWithinTenantAndNotAcrossTenants() {
        Project project = Project.createActive(tenant, "Old", null);
        when(projectRepository.findByIdAndTenant_Id(project.getId(), tenant.getId()))
                .thenReturn(Optional.of(project));
        when(projectRepository.existsByTenant_IdAndNameIgnoreCaseAndIdNot(tenant.getId(), "New", project.getId()))
                .thenReturn(false);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        ProjectResponse response = service.update(principal, project.getId(), new UpdateProjectRequest("New", "d"));
        assertEquals("New", response.name());

        UUID foreign = UUID.randomUUID();
        when(projectRepository.findByIdAndTenant_Id(foreign, tenant.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(principal, foreign, new UpdateProjectRequest("X", null)));
    }

    @Test
    void archiveWorksWithinTenantAndIsIdempotent() {
        Project project = Project.createActive(tenant, "P", null);
        when(projectRepository.findByIdAndTenant_Id(project.getId(), tenant.getId()))
                .thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        assertEquals(ProjectStatus.ARCHIVED, service.archive(principal, project.getId()).status());
        assertEquals(ProjectStatus.ARCHIVED, service.archive(principal, project.getId()).status());

        UUID foreign = UUID.randomUUID();
        when(projectRepository.findByIdAndTenant_Id(foreign, tenant.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.archive(principal, foreign));
    }
}