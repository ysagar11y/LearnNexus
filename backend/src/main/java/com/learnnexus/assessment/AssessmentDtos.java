package com.learnnexus.assessment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    // ---------------- Authoring ----------------

    public record AssessmentSummary(
            UUID id,
            UUID courseId,
            UUID lessonId,
            String title,
            String description,
            Assessment.Type type,
            Assessment.Status status,
            Integer timeLimitMinutes,
            int maxAttempts,
            int passingScore,
            Short questionsPerAttempt,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            BigDecimal negativeMarking,
            boolean showCorrectAnswers,
            int questionCount,
            BigDecimal totalPoints,
            long attemptCount,
            Integer averageScore,
            long awaitingGrading
    ) {}

    public record AssessmentDetail(
            AssessmentSummary summary,
            List<QuestionDetail> questions
    ) {}

    public record AssessmentRequest(
            @NotNull UUID courseId,
            UUID lessonId,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 1000) String description,
            Assessment.Type type,
            @Min(1) @Max(600) Integer timeLimitMinutes,
            @Min(0) @Max(20) int maxAttempts,
            @Min(0) @Max(100) int passingScore,
            Short questionsPerAttempt,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            BigDecimal negativeMarking,
            boolean showCorrectAnswers
    ) {}

    public record QuestionDetail(
            UUID id,
            Question.Type type,
            String prompt,
            String explanation,
            BigDecimal points,
            Question.Difficulty difficulty,
            int sortOrder,
            List<OptionDetail> options
    ) {}

    public record OptionDetail(
            UUID id,
            String label,
            boolean correct,
            int sortOrder
    ) {}

    public record QuestionRequest(
            Question.Type type,
            @NotBlank String prompt,
            String explanation,
            @NotNull BigDecimal points,
            Question.Difficulty difficulty,
            List<OptionRequest> options
    ) {}

    public record OptionRequest(
            @NotBlank String label,
            boolean correct
    ) {}

    public record StatusRequest(Assessment.Status status) {}

    // ---------------- Taking ----------------

    /**
     * A live attempt as the learner sees it. Deliberately omits which options are
     * correct — the client is never sent the answer key for an unsubmitted attempt.
     */
    public record AttemptView(
            UUID attemptId,
            UUID assessmentId,
            String title,
            String description,
            int attemptNumber,
            int maxAttempts,
            Integer timeLimitMinutes,
            Instant startedAt,
            Instant expiresAt,
            int passingScore,
            List<AttemptQuestion> questions,
            List<SavedAnswer> savedAnswers
    ) {}

    public record AttemptQuestion(
            UUID id,
            Question.Type type,
            String prompt,
            BigDecimal points,
            List<AttemptOption> options
    ) {}

    public record AttemptOption(UUID id, String label) {}

    public record SavedAnswer(UUID questionId, List<UUID> selectedOptions, String textAnswer) {}

    public record AnswerRequest(
            @NotNull UUID questionId,
            List<UUID> selectedOptions,
            String textAnswer
    ) {}

    public record SubmitRequest(List<AnswerRequest> answers) {}

    /** The result screen. Correct answers appear only when the author allowed it. */
    public record AttemptResult(
            UUID attemptId,
            UUID assessmentId,
            String title,
            Attempt.Status status,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            boolean passed,
            boolean requiresGrading,
            int passingScore,
            int attemptNumber,
            int maxAttempts,
            int attemptsRemaining,
            Instant submittedAt,
            int timeSpentSeconds,
            List<ReviewedQuestion> review
    ) {}

    public record ReviewedQuestion(
            UUID questionId,
            String prompt,
            Question.Type type,
            BigDecimal points,
            BigDecimal pointsAwarded,
            Boolean correct,
            List<UUID> selectedOptions,
            List<UUID> correctOptions,
            String textAnswer,
            String explanation,
            String feedback,
            List<OptionDetail> options
    ) {}

    // ---------------- Grading ----------------

    public record GradingQueueItem(
            UUID attemptId,
            UUID assessmentId,
            String assessmentTitle,
            UUID courseId,
            String courseTitle,
            UUID userId,
            String learnerName,
            Instant submittedAt,
            int pendingQuestions
    ) {}

    public record GradeRequest(
            @NotNull UUID questionId,
            @NotNull BigDecimal pointsAwarded,
            String feedback
    ) {}

    public record GradeSubmission(List<GradeRequest> grades) {}
}
