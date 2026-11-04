package com.worksphere.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.project.domain.Project;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.task.api.CreateTaskRequest;
import com.worksphere.task.api.TaskResponse;
import com.worksphere.task.api.UpdateTaskRequest;
import com.worksphere.task.api.UpdateTaskStatusRequest;
import com.worksphere.task.domain.Task;
import com.worksphere.task.domain.TaskPriority;
import com.worksphere.task.domain.TaskStatus;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.task.service.TaskService;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserRole;
import com.worksphere.user.repository.UserAccountRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.converter.HttpMessageNotReadableException;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private TaskService service;
    private Tenant tenant;
    private Project project;
    private UserAccount assignee;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, projectRepository, userAccountRepository);
        tenant = Tenant.createActiveFree("Acme", "acme");
        project = Project.createActive(tenant, "Alpha", null);
        assignee = UserAccount.createActiveTenantAdmin(tenant, "A", "B", "a@b.test", "hash");
        principal = new JwtPrincipal(assignee.getId(), tenant.getId(), UserRole.TENANT_ADMIN);
    }

    private Task existingTask() {
        return Task.createTodo(tenant, project, null, "Write docs", null, TaskPriority.LOW, null);
    }

    @Test
    void createSucceeds() {
        when(projectRepository.findByIdAndTenant_Id(project.getId(), tenant.getId())).thenReturn(Optional.of(project));
        when(userAccountRepository.findByIdAndTenant_Id(assignee.getId(), tenant.getId()))
                .thenReturn(Optional.of(assignee));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        TaskResponse response = service.create(principal,
                new CreateTaskRequest(project.getId(), assignee.getId(), " Ship it ", null, null, null));

        assertEquals("Ship it", response.title());
        assertEquals(TaskStatus.TODO, response.status());
        assertEquals(TaskPriority.MEDIUM, response.priority());
        assertEquals(project.getId(), response.projectId());
        assertEquals(assignee.getId(), response.assigneeId());
    }

    @Test
    void createFailsForAnotherTenantsProject() {
        UUID foreignProject = UUID.randomUUID();
        when(projectRepository.findByIdAndTenant_Id(foreignProject, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(principal, new CreateTaskRequest(foreignProject, null, "T", null, null, null)));
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void createFailsForAnotherTenantsAssignee() {
        UUID foreignUser = UUID.randomUUID();
        when(projectRepository.findByIdAndTenant_Id(project.getId(), tenant.getId())).thenReturn(Optional.of(project));
        when(userAccountRepository.findByIdAndTenant_Id(foreignUser, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(principal, new CreateTaskRequest(project.getId(), foreignUser, "T", null, null, null)));
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listIsTenantScoped() {
        when(taskRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(existingTask()));

        List<TaskResponse> result = service.list(principal, null, null, null);

        assertEquals(1, result.size());
        verify(taskRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void crossTenantReadUpdateAndStatusChangeAreNotFound() {
        UUID foreignTask = UUID.randomUUID();
        when(taskRepository.findByIdAndTenant_Id(foreignTask, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(principal, foreignTask));
        assertThrows(ResourceNotFoundException.class, () -> service.update(principal, foreignTask,
                new UpdateTaskRequest(null, null, null, "x", null, null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(principal, foreignTask,
                new UpdateTaskStatusRequest(TaskStatus.DONE)));
    }

    @Test
    void statusUpdateSucceeds() {
        Task task = existingTask();
        when(taskRepository.findByIdAndTenant_Id(task.getId(), tenant.getId())).thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        TaskResponse response = service.updateStatus(principal, task.getId(), new UpdateTaskStatusRequest(TaskStatus.DONE));

        assertEquals(TaskStatus.DONE, response.status());
    }

    @Test
    void blankTitleFailsValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        var violations = validator.validate(new CreateTaskRequest(project.getId(), null, "   ", null, null, null));

        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void invalidEnumInputIsRejectedByJsonBinding() {
        JsonMapper mapper = JsonMapper.builder().build();

        assertThrows(RuntimeException.class,
                () -> mapper.readValue("{\"status\":\"BOGUS\"}", UpdateTaskStatusRequest.class));
    }
}