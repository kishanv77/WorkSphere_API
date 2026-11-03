package com.worksphere.task.api;

import com.worksphere.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "Status is required")
        TaskStatus status) {
}