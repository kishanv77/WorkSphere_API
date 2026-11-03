package com.worksphere.project.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: omitted (null) fields are left unchanged. */
public record UpdateProjectRequest(
        @Size(max = 150, message = "Project name must be at most 150 characters")
        @Pattern(regexp = ".*\\S.*", message = "Project name must not be blank")
        String name,

        String description) {

    public UpdateProjectRequest {
        name = name == null ? null : name.trim();
    }
}