package com.worksphere.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.project.domain.Project;
import com.worksphere.project.repository.ProjectRepository;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TaskPermissionTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private TaskService service;
    private Tenant tenant;
    private UserAccount creator;
    private UserAccount assignee;
    private UserAccount outsider;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, projectRepository, userAccountRepository);
        tenant = Tenant.createActiveFree("Acme", "acme");
        Project project = Project.createActive(tenant, "P", null);
        creator = UserAccount.createActiveMember(tenant, "C", "C", "c@acme.test", "h");
        assignee = UserAccount.createActiveMember(tenant, "A", "A", "a@acme.test", "h");
        outsider = UserAccount.createActiveMember(tenant, "O", "O", "o@acme.test", "h");
        task = Task.createTodo(tenant, project, assignee, creator, "T", null, TaskPriority.LOW, null);
    }

    private JwtPrincipal member(UserAccount user) {
        return new JwtPrincipal(user.getId(), tenant.getId(), UserRole.MEMBER);
    }

    private void stubTask() {
        when(taskRepository.findByIdAndTenant_Id(task.getId(), tenant.getId())).thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);
    }

    @Test
    void assigneeCanChangeStatus() {
        stubTask();
        assertEquals(TaskStatus.DONE,
                service.updateStatus(member(assignee), task.getId(), new UpdateTaskStatusRequest(TaskStatus.DONE)).status());
    }

    @Test
    void creatorCanChangeStatus() {
        stubTask();
        assertEquals(TaskStatus.IN_PROGRESS,
                service.updateStatus(member(creator), task.getId(), new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS)).status());
    }

    @Test
    void unrelatedMemberIsForbidden() {
        when(taskRepository.findByIdAndTenant_Id(task.getId(), tenant.getId())).thenReturn(Optional.of(task));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(
                member(outsider), task.getId(), new UpdateTaskStatusRequest(TaskStatus.DONE)));
    }

    @Test
    void adminCanChangeAnyTask() {
        stubTask();
        JwtPrincipal admin = new JwtPrincipal(outsider.getId(), tenant.getId(), UserRole.TENANT_ADMIN);
        assertEquals(TaskStatus.DONE,
                service.updateStatus(admin, task.getId(), new UpdateTaskStatusRequest(TaskStatus.DONE)).status());
    }
}