package com.worksphere.dashboard.service;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.dashboard.api.DashboardResponse;
import com.worksphere.project.domain.ProjectStatus;
import com.worksphere.project.repository.ProjectRepository;
import com.worksphere.task.domain.TaskStatus;
import com.worksphere.task.repository.TaskRepository;
import com.worksphere.user.domain.UserStatus;
import com.worksphere.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock = Clock.systemUTC();

    public DashboardService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserAccountRepository userAccountRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary(JwtPrincipal principal) {
        UUID tenantId = principal.tenantId();
        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            byStatus.put(status, taskRepository.countByTenant_IdAndStatus(tenantId, status));
        }
        return new DashboardResponse(
                byStatus,
                taskRepository.countByTenant_IdAndDueDateBeforeAndStatusNot(
                        tenantId, LocalDate.now(clock), TaskStatus.DONE),
                taskRepository.countByTenant_IdAndAssignee_IdAndStatusNot(
                        tenantId, principal.userId(), TaskStatus.DONE),
                projectRepository.countByTenant_IdAndStatus(tenantId, ProjectStatus.ACTIVE),
                projectRepository.countByTenant_IdAndStatus(tenantId, ProjectStatus.ARCHIVED),
                userAccountRepository.countByTenant_IdAndStatus(tenantId, UserStatus.ACTIVE));
    }
}