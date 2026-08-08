package com.learnnexus.assessment;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempt_answers")
@Getter
@Setter
@NoArgsConstructor
public class AttemptAnswer extends TenantScoped {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_options", nullable = false)
    private UUID[] selectedOptions = new UUID[0];

    @Column(name = "text_answer", columnDefinition = "text")
    private String textAnswer;

    /** Null until graded — meaningful for essays awaiting a human. */
    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "points_awarded", nullable = false)
    private BigDecimal pointsAwarded = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();
}
