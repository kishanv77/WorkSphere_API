package com.worksphere.project.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.api.PageRequests;
import com.worksphere.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(principal, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageRequests.ok(projectService.list(principal, page, size));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable UUID projectId) {
        return projectService.get(principal, projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(principal, projectId, request);
    }

    @PatchMapping("/{projectId}/archive")
    public ProjectResponse archive(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable UUID projectId) {
        return projectService.archive(principal, projectId);
    }
}