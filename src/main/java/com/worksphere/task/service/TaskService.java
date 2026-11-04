package com.worksphere.task.service;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.api.PageRequests;
import com.worksphere.common.exception.ConflictException;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.project.domain.Project;
import com.worksphere.project.domain.ProjectStatus;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.task.api.CreateTaskRequest;
import com.worksphere.task.api.TaskResponse;
import com.worksphere.task.api.UpdateTaskRequest;
import com.worksphere.task.api.UpdateTaskStatusRequest;
import com.worksphere.task.domain.Task;
import com.worksphere.task.domain.TaskPriority;
import com.worksphere.task.domain.TaskStatus;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final String TASK_NOT_FOUND = "Task not found";
    private static final String PROJECT_NOT_FOUND = "Project not found";
    private static final String ASSIGNEE_NOT_FOUND = "Assignee not found";

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserAccountRepository userAccountRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserAccountRepository userAccountRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public TaskResponse create(JwtPrincipal principal, CreateTaskRequest request) {
        UUID tenantId = principal.tenantId();
        Project project = findProject(tenantId, request.projectId());
        requireActive(project);
        UserAccount assignee = request.assigneeId() == null ? null : findAssignee(tenantId, request.assigneeId());

        Task task = Task.createTodo(
                project.getTenant(),
                project,
                assignee,
                request.title(),
                blankToNull(request.description()),
                request.priority() != null ? request.priority() : TaskPriority.MEDIUM,
                request.dueDate());
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(
            JwtPrincipal principal, UUID projectId, TaskStatus status, UUID assigneeId, int page, int size) {
        UUID tenantId = principal.tenantId();
        List<Specification<Task>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.equal(root.get("tenant").get("id"), tenantId));
        if (projectId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("project").get("id"), projectId));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (assigneeId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
        }
        return taskRepository
                .findAll(Specification.allOf(filters), PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(JwtPrincipal principal, UUID taskId) {
        return TaskResponse.from(findTask(principal.tenantId(), taskId));
    }

    @Transactional
    public TaskResponse update(JwtPrincipal principal, UUID taskId, UpdateTaskRequest request) {
        UUID tenantId = principal.tenantId();
        Task task = findTask(tenantId, taskId);

        Project project = task.getProject();
        if (request.projectId() != null && !request.projectId().equals(project.getId())) {
            project = findProject(tenantId, request.projectId());
            requireActive(project);
        }

        UserAccount assignee = task.getAssignee();
        if (Boolean.TRUE.equals(request.unassign())) {
            assignee = null;
        } else if (request.assigneeId() != null) {
            assignee = findAssignee(tenantId, request.assigneeId());
        }

        task.updateDetails(
                project,
                assignee,
                request.title() != null ? request.title() : task.getTitle(),
                request.description() != null ? blankToNull(request.description()) : task.getDescription(),
                request.priority() != null ? request.priority() : task.getPriority(),
                Boolean.TRUE.equals(request.clearDueDate())
                        ? null
                        : request.dueDate() != null ? request.dueDate() : task.getDueDate());
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse updateStatus(JwtPrincipal principal, UUID taskId, UpdateTaskStatusRequest request) {
        Task task = findTask(principal.tenantId(), taskId);
        task.changeStatus(request.status());
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    private Task findTask(UUID tenantId, UUID taskId) {
        return taskRepository.findByIdAndTenant_Id(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TASK_NOT_FOUND));
    }

    private Project findProject(UUID tenantId, UUID projectId) {
        return projectRepository.findByIdAndTenant_Id(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(PROJECT_NOT_FOUND));
    }

    private UserAccount findAssignee(UUID tenantId, UUID assigneeId) {
        return userAccountRepository.findByIdAndTenant_Id(assigneeId, tenantId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ASSIGNEE_NOT_FOUND));
    }

    private static void requireActive(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ConflictException("Tasks cannot be added to an archived project");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}