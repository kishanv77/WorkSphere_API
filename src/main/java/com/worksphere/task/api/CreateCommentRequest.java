package com.worksphere.task.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Comment text is required")
        @Size(max = 2000, message = "Comment must be at most 2000 characters")
        String body) {

    public CreateCommentRequest {
        body = body == null ? null : body.trim();
    }
}