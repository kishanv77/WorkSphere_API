package com.worksphere.project.service;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.DuplicateResourceException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.project.api.CreateProjectRequest;
import com.worksphere.project.api.ProjectResponse;
import com.worksphere.project.api.UpdateProjectRequest;
import com.worksphere.project.domain.Project;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String NOT_FOUND = "Project not found";
    private static final String DUPLICATE = "A project with this name already exists";

    private final ProjectRepository projectRepository;
    private final TenantRepository tenantRepository;

    public ProjectService(ProjectRepository projectRepository, TenantRepository tenantRepository) {
        this.projectRepository = projectRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public ProjectResponse create(JwtPrincipal principal, CreateProjectRequest request) {
        UUID tenantId = principal.tenantId();
        if (projectRepository.existsByTenant_IdAndNameIgnoreCase(tenantId, request.name())) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Project project = Project.createActive(tenant, request.name(), blankToNull(request.description()));
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(JwtPrincipal principal) {
        return projectRepository.findAllByTenant_IdOrderByCreatedAtDesc(principal.tenantId()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(JwtPrincipal principal, UUID projectId) {
        return ProjectResponse.from(find(principal, projectId));
    }

    @Transactional
    public ProjectResponse update(JwtPrincipal principal, UUID projectId, UpdateProjectRequest request) {
        Project project = find(principal, projectId);
        String name = request.name() != null ? request.name() : project.getName();
        String description = request.description() != null
                ? blankToNull(request.description())
                : project.getDescription();
        if (!name.equalsIgnoreCase(project.getName())
                && projectRepository.existsByTenant_IdAndNameIgnoreCaseAndIdNot(
                        principal.tenantId(), name, projectId)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        project.updateDetails(name, description);
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    /** Idempotent: archiving an already archived project succeeds and changes nothing. */
    @Transactional
    public ProjectResponse archive(JwtPrincipal principal, UUID projectId) {
        Project project = find(principal, projectId);
        project.archive();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    private Project find(JwtPrincipal principal, UUID projectId) {
        return projectRepository.findByIdAndTenant_Id(projectId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}