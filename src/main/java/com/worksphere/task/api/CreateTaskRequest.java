package com.worksphere.task.api;

import com.worksphere.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        UUID assigneeId,

        @NotBlank(message = "Task title is required")
        @Size(max = 200, message = "Task title must be at most 200 characters")
        String title,

        String description,

        /** Defaults to MEDIUM when omitted. */
        TaskPriority priority,

        LocalDate dueDate) {

    public CreateTaskRequest {
        title = title == null ? null : title.trim();
    }
}