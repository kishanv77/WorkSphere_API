package com.worksphere.task.repository;

import com.worksphere.task.domain.Task;
import com.worksphere.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndTenant_Id(UUID taskId, UUID tenantId);

    long countByTenant_IdAndStatus(UUID tenantId, TaskStatus status);

    long countByTenant_IdAndDueDateBeforeAndStatusNot(UUID tenantId, LocalDate date, TaskStatus status);

    long countByTenant_IdAndAssignee_IdAndStatusNot(UUID tenantId, UUID assigneeId, TaskStatus status);
}