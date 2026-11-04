package com.worksphere.task.domain;

import com.worksphere.project.domain.Project;
import com.worksphere.tenant.domain.Tenant;
import com.worksphere.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserAccount assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", updatable = false)
    private UserAccount createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
    }

    private Task(Tenant tenant, Project project, UserAccount assignee, String title, String description,
            TaskPriority priority, LocalDate dueDate) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.project = project;
        this.assignee = assignee;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.priority = priority;
        this.dueDate = dueDate;
    }

    public static Task createTodo(Tenant tenant, Project project, UserAccount assignee, String title,
            String description, TaskPriority priority, LocalDate dueDate) {
        return new Task(tenant, project, assignee, title, description, priority, dueDate);
    }

    public static Task createTodo(Tenant tenant, Project project, UserAccount assignee, UserAccount createdBy,
            String title, String description, TaskPriority priority, LocalDate dueDate) {
        Task task = new Task(tenant, project, assignee, title, description, priority, dueDate);
        task.createdBy = createdBy;
        return task;
    }

    public void updateDetails(Project project, UserAccount assignee, String title, String description,
            TaskPriority priority, LocalDate dueDate) {
        this.project = project;
        this.assignee = assignee;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Project getProject() {
        return project;
    }

    public UserAccount getAssignee() {
        return assignee;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}