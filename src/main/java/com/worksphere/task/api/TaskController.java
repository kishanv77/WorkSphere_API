package com.worksphere.task.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.api.PageRequests;
import com.worksphere.task.domain.TaskStatus;
import com.worksphere.task.service.TaskService;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(principal, request));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageRequests.ok(taskService.list(principal, projectId, status, assigneeId, page, size));
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable UUID taskId) {
        return taskService.get(principal, taskId);
    }

    @PatchMapping("/{taskId}")
    public TaskResponse update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(principal, taskId, request);
    }

    @PatchMapping("/{taskId}/status")
    public TaskResponse updateStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(principal, taskId, request);
    }
}