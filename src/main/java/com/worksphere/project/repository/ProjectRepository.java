package com.worksphere.project.repository;

import com.worksphere.project.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Project> findByIdAndTenant_Id(UUID projectId, UUID tenantId);

    boolean existsByTenant_IdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenant_IdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID projectId);
}