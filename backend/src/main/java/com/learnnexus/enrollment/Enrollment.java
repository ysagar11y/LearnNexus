package com.learnnexus.enrollment;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment extends TenantScoped {

    public enum Status { ACTIVE, COMPLETED, EXPIRED, WITHDRAWN, WAITLISTED }

    /** How the learner came to be enrolled; drives reporting on assignment channels. */
    public enum Source { MANUAL, SELF, INVITE, RULE, IMPORT, API, PATH }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.MANUAL;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "progress_percent", nullable = false)
    private short progressPercent = 0;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt = Instant.now();

    public boolean isOverdue() {
        return status == Status.ACTIVE && dueAt != null && dueAt.isBefore(Instant.now());
    }

    public boolean isOpen() {
        return status == Status.ACTIVE || status == Status.COMPLETED;
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.progressPercent = 100;
        this.completedAt = Instant.now();
    }
}
