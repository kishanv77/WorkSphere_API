package com.worksphere.dashboard.api;

import com.worksphere.task.domain.TaskStatus;
import java.util.Map;

public record DashboardResponse(
        Map<TaskStatus, Long> tasksByStatus,
        long overdueTasks,
        long myOpenTasks,
        long activeProjects,
        long archivedProjects,
        long activeMembers) {
}