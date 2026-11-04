package com.worksphere.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must be at most 150 characters")
        String name,

        String description) {

    public CreateProjectRequest {
        name = name == null ? null : name.trim();
    }
}