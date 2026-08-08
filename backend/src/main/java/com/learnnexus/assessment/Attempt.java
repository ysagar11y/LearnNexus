package com.learnnexus.assessment;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor
public class Attempt extends TenantScoped {

    public enum Status { IN_PROGRESS, SUBMITTED, GRADED, EXPIRED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "attempt_number", nullable = false)
    private short attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_PROGRESS;

    @Column(nullable = false)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean passed = false;

    /** True when the attempt contains essay answers awaiting an instructor. */
    @Column(name = "requires_grading", nullable = false)
    private boolean requiresGrading = false;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "graded_by")
    private UUID gradedBy;

    public boolean isTimedOut() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
