package com.worksphere.task.repository;

import com.worksphere.task.domain.TaskComment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    Page<TaskComment> findAllByTask_IdAndTenant_Id(UUID taskId, UUID tenantId, Pageable pageable);
}