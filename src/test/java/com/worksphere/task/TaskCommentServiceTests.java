package com.worksphere.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.common.exception.ResourceNotFoundException;
import com.worksphere.project.domain.Project;
import com.worksphere.task.api.CommentResponse;
import com.worksphere.task.api.CreateCommentRequest;
import com.worksphere.task.domain.Task;
import com.worksphere.task.domain.TaskComment;
import com.worksphere.task.domain.TaskPriority;
import com.worksphere.task.repository.TaskCommentRepository;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.task.service.TaskCommentService;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.domain.UserRole;
import com.worksphere.user.repository.UserAccountRepository;
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

@ExtendWith(MockitoExtension.class)
class TaskCommentServiceTests {

    @Mock
    private TaskCommentRepository taskCommentRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private TaskCommentService service;
    private Tenant tenant;
    private UserAccount user;
    private Task task;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new TaskCommentService(taskCommentRepository, taskRepository, userAccountRepository);
        tenant = Tenant.createActiveFree("Acme", "acme");
        user = UserAccount.createActiveTenantAdmin(tenant, "Ada", "Admin", "ada@acme.test", "h");
        task = Task.createTodo(tenant, Project.createActive(tenant, "P", null), null, "T", null, TaskPriority.LOW, null);
        principal = new JwtPrincipal(user.getId(), tenant.getId(), UserRole.TENANT_ADMIN);
    }

    @Test
    void addCreatesCommentByCurrentUser() {
        when(taskRepository.findByIdAndTenant_Id(task.getId(), tenant.getId())).thenReturn(Optional.of(task));
        when(userAccountRepository.findByIdAndTenant_Id(user.getId(), tenant.getId())).thenReturn(Optional.of(user));
        when(taskCommentRepository.saveAndFlush(any(TaskComment.class))).thenAnswer(i -> i.getArgument(0));

        CommentResponse response = service.add(principal, task.getId(), new CreateCommentRequest(" Looks good "));

        assertEquals("Looks good", response.body());
        assertEquals("Ada Admin", response.authorName());
        assertEquals(user.getId(), response.authorId());
    }

    @Test
    void commentOnAnotherTenantsTaskIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(taskRepository.findByIdAndTenant_Id(foreign, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.add(principal, foreign, new CreateCommentRequest("hi")));
        assertThrows(ResourceNotFoundException.class, () -> service.list(principal, foreign, 0, 50));
        verify(taskCommentRepository, never()).saveAndFlush(any());
    }

    @Test
    void listIsTenantScoped() {
        when(taskRepository.findByIdAndTenant_Id(task.getId(), tenant.getId())).thenReturn(Optional.of(task));
        TaskComment comment = TaskComment.create(task, user, "hello");
        when(taskCommentRepository.findAllByTask_IdAndTenant_Id(eq(task.getId()), eq(tenant.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));

        assertEquals(1, service.list(principal, task.getId(), 0, 50).getTotalElements());
    }
}