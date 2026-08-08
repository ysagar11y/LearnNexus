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
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment extends TenantScoped {

    public enum Type { QUIZ, EXAM, SURVEY }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** When set, the assessment is embedded in a lesson and gates its completion. */
    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type = Type.QUIZ;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts = 3;

    @Column(name = "passing_score", nullable = false)
    private short passingScore = 70;

    /** Draws a random subset from the question pool when set. */
    @Column(name = "questions_per_attempt")
    private Short questionsPerAttempt;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions = true;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions = true;

    @Column(name = "negative_marking", nullable = false)
    private BigDecimal negativeMarking = BigDecimal.ZERO;

    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean allowsUnlimitedAttempts() {
        return maxAttempts <= 0;
    }
}
