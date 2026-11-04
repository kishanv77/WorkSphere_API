package com.worksphere.task.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.api.PageRequests;
import com.worksphere.task.service.TaskCommentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
public class TaskCommentController {

    private final TaskCommentService taskCommentService;

    public TaskCommentController(TaskCommentService taskCommentService) {
        this.taskCommentService = taskCommentService;
    }

    @PostMapping
    public ResponseEntity<CommentResponse> add(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskCommentService.add(principal, taskId, request));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageRequests.ok(taskCommentService.list(principal, taskId, page, size));
    }
}