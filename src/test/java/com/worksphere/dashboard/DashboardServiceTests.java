package com.worksphere.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.dashboard.api.DashboardResponse;
import com.worksphere.dashboard.service.DashboardService;
import com.worksphere.project.domain.ProjectStatus;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.task.domain.TaskStatus;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.user.domain.UserRole;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void summaryCountsOnlyTheCallersTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtPrincipal principal = new JwtPrincipal(userId, tenantId, UserRole.MEMBER);
        when(taskRepository.countByTenant_IdAndStatus(tenantId, TaskStatus.TODO)).thenReturn(3L);
        when(taskRepository.countByTenant_IdAndStatus(tenantId, TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(taskRepository.countByTenant_IdAndStatus(tenantId, TaskStatus.DONE)).thenReturn(1L);
        when(taskRepository.countByTenant_IdAndDueDateBeforeAndStatusNot(eq(tenantId), any(LocalDate.class), eq(TaskStatus.DONE)))
                .thenReturn(4L);
        when(taskRepository.countByTenant_IdAndAssignee_IdAndStatusNot(tenantId, userId, TaskStatus.DONE)).thenReturn(5L);
        when(projectRepository.countByTenant_IdAndStatus(tenantId, ProjectStatus.ACTIVE)).thenReturn(6L);
        when(projectRepository.countByTenant_IdAndStatus(tenantId, ProjectStatus.ARCHIVED)).thenReturn(7L);
        when(userAccountRepository.countByTenant_IdAndStatus(tenantId, UserStatus.ACTIVE)).thenReturn(8L);

        DashboardResponse response = new DashboardService(taskRepository, projectRepository, userAccountRepository)
                .summary(principal);

        assertEquals(3L, response.tasksByStatus().get(TaskStatus.TODO));
        assertEquals(2L, response.tasksByStatus().get(TaskStatus.IN_PROGRESS));
        assertEquals(1L, response.tasksByStatus().get(TaskStatus.DONE));
        assertEquals(4L, response.overdueTasks());
        assertEquals(5L, response.myOpenTasks());
        assertEquals(6L, response.activeProjects());
        assertEquals(7L, response.archivedProjects());
        assertEquals(8L, response.activeMembers());
    }
}