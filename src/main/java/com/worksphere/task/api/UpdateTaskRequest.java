package com.worksphere.task.api;

import com.worksphere.task.domain.TaskPriority;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update: omitted (null) fields are left unchanged. Set {@code unassign} to true to
 * remove the assignee; {@code clearDueDate} to remove the due date.
 */
public record UpdateTaskRequest(
        UUID projectId,

        UUID assigneeId,

        Boolean unassign,

        @Size(max = 200, message = "Task title must be at most 200 characters")
        @Pattern(regexp = ".*\\S.*", message = "Task title must not be blank")
        String title,

        String description,

        TaskPriority priority,

        LocalDate dueDate,

        Boolean clearDueDate) {

    public UpdateTaskRequest {
        title = title == null ? null : title.trim();
    }
}