package com.worksphere.task.service;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.api.PageRequests;
import com.worksphere.common.exception.AuthenticationInvalidException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.task.api.CommentResponse;
import com.worksphere.task.api.CreateCommentRequest;
import com.worksphere.task.domain.Task;
import com.worksphere.task.domain.TaskComment;
import com.worksphere.task.repository.TaskCommentRepository;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommentService {

    private static final String TASK_NOT_FOUND = "Task not found";

    private final TaskCommentRepository taskCommentRepository;
    private final TaskRepository taskRepository;
    private final UserAccountRepository userAccountRepository;

    public TaskCommentService(
            TaskCommentRepository taskCommentRepository,
            TaskRepository taskRepository,
            UserAccountRepository userAccountRepository) {
        this.taskCommentRepository = taskCommentRepository;
        this.taskRepository = taskRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public CommentResponse add(JwtPrincipal principal, UUID taskId, CreateCommentRequest request) {
        Task task = findTask(principal, taskId);
        UserAccount author = userAccountRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(AuthenticationInvalidException::new);
        TaskComment comment = TaskComment.create(task, author, request.body());
        return CommentResponse.from(taskCommentRepository.saveAndFlush(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> list(JwtPrincipal principal, UUID taskId, int page, int size) {
        findTask(principal, taskId);
        return taskCommentRepository
                .findAllByTask_IdAndTenant_Id(
                        taskId, principal.tenantId(), PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")))
                .map(CommentResponse::from);
    }

    private Task findTask(JwtPrincipal principal, UUID taskId) {
        return taskRepository.findByIdAndTenant_Id(taskId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(TASK_NOT_FOUND));
    }
}