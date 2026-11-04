package com.worksphere.task.api;

import com.worksphere.task.domain.TaskComment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID taskId,
        UUID authorId,
        String authorName,
        String body,
        Instant createdAt) {

    public static CommentResponse from(TaskComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}