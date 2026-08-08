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
@Table(name = "lesson_progress")
@Getter
@Setter
@NoArgsConstructor
public class LessonProgress extends TenantScoped {

    public enum Status { NOT_STARTED, IN_PROGRESS, COMPLETED }

    /**
     * Fraction of a timed lesson that must be watched before it counts as done.
     * Below 100% so that trailing credits or a slightly short recording do not
     * strand a learner one second from completion.
     */
    public static final double COMPLETION_THRESHOLD = 0.9;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "seconds_watched", nullable = false)
    private int secondsWatched = 0;

    @Column(name = "last_position_seconds", nullable = false)
    private int lastPositionSeconds = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void complete() {
        if (status != Status.COMPLETED) {
            status = Status.COMPLETED;
            completedAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
}
