package com.learnnexus.assessment;

import com.learnnexus.audit.AuditService;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.enrollment.EnrollmentRepositories;
import com.learnnexus.enrollment.EnrollmentService;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.notification.NotificationService;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Taking and grading assessments.
 *
 * <p>The attempt's question set is materialised as empty answer rows the moment
 * it starts. That fixes which questions a shuffled or sampled attempt contains,
 * so a learner cannot reroll for an easier draw by reloading, and a later edit to
 * the question pool cannot change an attempt that is already under way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AssessmentRepositories.AssessmentRepository assessmentRepository;
    private final AssessmentRepositories.QuestionRepository questionRepository;
    private final AssessmentRepositories.QuestionOptionRepository optionRepository;
    private final AssessmentRepositories.AttemptRepository attemptRepository;
    private final AssessmentRepositories.AttemptAnswerRepository answerRepository;
    private final EnrollmentRepositories.EnrollmentRepository enrollmentRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;

    // =================================================================
    // Starting and resuming
    // =================================================================

    @Transactional
    public AssessmentDtos.AttemptView start(UUID assessmentId) {
        UUID userId = CurrentUser.requireId();
        Assessment assessment = requireAssessment(assessmentId);

        if (assessment.getStatus() != Assessment.Status.PUBLISHED) {
            throw ApiException.badRequest("assessment_unavailable", "This assessment is not available yet.");
        }

        var enrollment = enrollmentRepository.findByCourseIdAndUserId(assessment.getCourseId(), userId)
                .orElseThrow(() -> ApiException.forbidden("You are not enrolled in this course."));

        // Resume rather than start a second attempt if one is still open.
        var existing = attemptRepository.findInProgress(assessmentId, userId);
        if (existing.isPresent()) {
            Attempt attempt = existing.get();
            if (attempt.isTimedOut()) {
                autoSubmitExpired(attempt, assessment);
                throw ApiException.conflict("attempt_expired",
                        "Your previous attempt ran out of time and has been submitted.");
            }
            return toAttemptView(attempt, assessment);
        }

        short lastNumber = attemptRepository.lastAttemptNumber(assessmentId, userId);
        if (!assessment.allowsUnlimitedAttempts() && lastNumber >= assessment.getMaxAttempts()) {
            throw ApiException.conflict("no_attempts_left",
                    "You have used all " + assessment.getMaxAttempts() + " attempts for this assessment.");
        }

        Attempt attempt = new Attempt();
        attempt.setAssessmentId(assessmentId);
        attempt.setUserId(userId);
        attempt.setEnrollmentId(enrollment.getId());
        attempt.setAttemptNumber((short) (lastNumber + 1));
        if (assessment.getTimeLimitMinutes() != null) {
            attempt.setExpiresAt(Instant.now().plus(Duration.ofMinutes(assessment.getTimeLimitMinutes())));
        }
        attemptRepository.save(attempt);

        for (Question question : selectQuestions(assessment)) {
            AttemptAnswer placeholder = new AttemptAnswer();
            placeholder.setAttemptId(attempt.getId());
            placeholder.setQuestionId(question.getId());
            answerRepository.save(placeholder);
        }

        attempt.setMaxScore(totalPointsFor(attempt.getId()));
        attemptRepository.save(attempt);

        return toAttemptView(attempt, assessment);
    }

    private List<Question> selectQuestions(Assessment assessment) {
        List<Question> pool = new ArrayList<>(questionRepository.findByAssessment(assessment.getId()));
        if (pool.isEmpty()) {
            throw ApiException.badRequest("assessment_empty", "This assessment has no questions yet.");
        }
        if (assessment.isShuffleQuestions()) {
            Collections.shuffle(pool);
        }
        Short sampleSize = assessment.getQuestionsPerAttempt();
        if (sampleSize != null && sampleSize > 0 && sampleSize < pool.size()) {
            return pool.subList(0, sampleSize);
        }
        return pool;
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.AttemptView resume(UUID attemptId) {
        Attempt attempt = requireOwnAttempt(attemptId);
        if (attempt.getStatus() != Attempt.Status.IN_PROGRESS) {
            throw ApiException.conflict("attempt_closed", "This attempt has already been submitted.");
        }
        return toAttemptView(attempt, requireAssessment(attempt.getAssessmentId()));
    }

    // =================================================================
    // Answering
    // =================================================================

    @Transactional
    public void saveAnswer(UUID attemptId, AssessmentDtos.AnswerRequest request) {
        Attempt attempt = requireOwnAttempt(attemptId);
        assertOpen(attempt);

        AttemptAnswer answer = answerRepository
                .findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseThrow(() -> ApiException.badRequest("unknown_question",
                        "That question is not part of this attempt."));

        answer.setSelectedOptions(request.selectedOptions() == null
                ? new UUID[0] : request.selectedOptions().toArray(new UUID[0]));
        answer.setTextAnswer(request.textAnswer());
        answer.setAnsweredAt(Instant.now());
        answerRepository.save(answer);
    }

    @Transactional
    public AssessmentDtos.AttemptResult submit(UUID attemptId, AssessmentDtos.SubmitRequest request) {
        Attempt attempt = requireOwnAttempt(attemptId);
        assertOpen(attempt);

        Assessment assessment = requireAssessment(attempt.getAssessmentId());

        if (request != null && request.answers() != null) {
            for (AssessmentDtos.AnswerRequest answer : request.answers()) {
                answerRepository.findByAttemptIdAndQuestionId(attemptId, answer.questionId())
                        .ifPresent(stored -> {
                            stored.setSelectedOptions(answer.selectedOptions() == null
                                    ? new UUID[0] : answer.selectedOptions().toArray(new UUID[0]));
                            stored.setTextAnswer(answer.textAnswer());
                            stored.setAnsweredAt(Instant.now());
                            answerRepository.save(stored);
                        });
            }
        }

        grade(attempt, assessment);
        return result(attemptId);
    }

    private void autoSubmitExpired(Attempt attempt, Assessment assessment) {
        log.debug("Auto-submitting expired attempt {}", attempt.getId());
        grade(attempt, assessment);
    }

    // =================================================================
    // Grading
    // =================================================================

    private void grade(Attempt attempt, Assessment assessment) {
        List<AttemptAnswer> answers = answerRepository.findByAttempt(attempt.getId());
        Map<UUID, Question> questions = questionRepository
                .findAllById(answers.stream().map(AttemptAnswer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        Map<UUID, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestions(questions.keySet()).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        BigDecimal score = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        boolean needsHuman = false;

        for (AttemptAnswer answer : answers) {
            Question question = questions.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            maxScore = maxScore.add(question.getPoints());

            if (!question.getType().isAutoGradable()) {
                // Leave essays for an instructor; correct stays null until then.
                needsHuman = true;
                continue;
            }

            List<QuestionOption> options = optionsByQuestion.getOrDefault(question.getId(), List.of());
            boolean correct = isCorrect(question, options, answer);
            answer.setCorrect(correct);

            if (correct) {
                answer.setPointsAwarded(question.getPoints());
                score = score.add(question.getPoints());
            } else if (isAnswered(answer) && assessment.getNegativeMarking().signum() > 0) {
                // Negative marking only bites on a wrong answer, never on a blank one,
                // so guessing is penalised but leaving a question out is not.
                BigDecimal penalty = question.getPoints().multiply(assessment.getNegativeMarking());
                answer.setPointsAwarded(penalty.negate());
                score = score.subtract(penalty);
            } else {
                answer.setPointsAwarded(BigDecimal.ZERO);
            }
            answerRepository.save(answer);
        }

        // Negative marking can drive a total below zero; a negative percentage is
        // not meaningful to a learner, so the floor is zero.
        score = score.max(BigDecimal.ZERO);

        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercentage(percentageOf(score, maxScore));
        attempt.setRequiresGrading(needsHuman);
        attempt.setStatus(needsHuman ? Attempt.Status.SUBMITTED : Attempt.Status.GRADED);
        attempt.setSubmittedAt(Instant.now());
        attempt.setTimeSpentSeconds((int) Duration.between(attempt.getStartedAt(), Instant.now()).toSeconds());
        if (!needsHuman) {
            attempt.setGradedAt(Instant.now());
            attempt.setPassed(attempt.getPercentage().doubleValue() >= assessment.getPassingScore());
        }
        attemptRepository.save(attempt);

        if (attempt.isPassed()) {
            enrollmentService.onAssessmentPassed(assessment.getCourseId(), attempt.getUserId(),
                    assessment.getLessonId());
        }
        if (!needsHuman) {
            notificationService.attemptGraded(attempt.getUserId(), assessment.getTitle(),
                    attempt.isPassed(), attempt.getPercentage().intValue());
        }
    }

    private boolean isCorrect(Question question, List<QuestionOption> options, AttemptAnswer answer) {
        return switch (question.getType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                Set<UUID> correctIds = correctOptionIds(options);
                Set<UUID> selected = Set.of(answer.getSelectedOptions());
                yield selected.size() == 1 && correctIds.containsAll(selected);
            }
            case MULTI_CHOICE -> {
                // All-or-nothing: a partially correct multi-select is not a pass.
                Set<UUID> correctIds = correctOptionIds(options);
                Set<UUID> selected = new LinkedHashSet<>(List.of(answer.getSelectedOptions()));
                yield !correctIds.isEmpty() && correctIds.equals(selected);
            }
            case SHORT_ANSWER -> {
                String given = normalise(answer.getTextAnswer());
                yield !given.isEmpty() && options.stream()
                        .filter(QuestionOption::isCorrect)
                        .anyMatch(option -> normalise(option.getLabel()).equals(given));
            }
            case ESSAY -> false;
        };
    }

    private static Set<UUID> correctOptionIds(List<QuestionOption> options) {
        return options.stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());
    }

    private static boolean isAnswered(AttemptAnswer answer) {
        return answer.getSelectedOptions().length > 0
                || (answer.getTextAnswer() != null && !answer.getTextAnswer().isBlank());
    }

    /** Case- and whitespace-insensitive comparison for short answers. */
    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static BigDecimal percentageOf(BigDecimal score, BigDecimal maxScore) {
        if (maxScore.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return score.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100));
    }

    // ---------------- Manual grading ----------------

    @Transactional(readOnly = true)
    public PageResponse<AssessmentDtos.GradingQueueItem> gradingQueue(UUID assessmentId, int page, int size) {
        var attempts = attemptRepository.findAwaitingGrading(assessmentId,
                PageRequest.of(page, Math.min(size, 50)));

        return PageResponse.of(attempts, attempt -> {
            Assessment assessment = assessmentRepository.findById(attempt.getAssessmentId()).orElse(null);
            String courseTitle = assessment == null ? null
                    : courseRepository.findActiveById(assessment.getCourseId())
                            .map(course -> course.getTitle()).orElse(null);

            long pending = answerRepository.findByAttempt(attempt.getId()).stream()
                    .filter(answer -> answer.getCorrect() == null)
                    .count();

            String learnerName = jdbc.queryOne(
                    "select first_name, last_name from users where tenant_id = ? and id = ?",
                    (rs, rowNum) -> (rs.getString("first_name") + " "
                            + Objects.requireNonNullElse(rs.getString("last_name"), "")).trim(),
                    attempt.getUserId()).orElse("Unknown learner");

            return new AssessmentDtos.GradingQueueItem(
                    attempt.getId(), attempt.getAssessmentId(),
                    assessment == null ? null : assessment.getTitle(),
                    assessment == null ? null : assessment.getCourseId(),
                    courseTitle, attempt.getUserId(), learnerName,
                    attempt.getSubmittedAt(), (int) pending);
        });
    }

    @Transactional
    public AssessmentDtos.AttemptResult gradeManually(UUID attemptId, AssessmentDtos.GradeSubmission submission) {
        assertCanGrade();

        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt", attemptId));
        if (attempt.getStatus() != Attempt.Status.SUBMITTED) {
            throw ApiException.conflict("not_awaiting_grading", "This attempt is not waiting to be graded.");
        }

        Assessment assessment = requireAssessment(attempt.getAssessmentId());
        Map<UUID, Question> questions = questionRepository
                .findAllById(answerRepository.findByAttempt(attemptId).stream()
                        .map(AttemptAnswer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));

        for (AssessmentDtos.GradeRequest grade : submission.grades()) {
            AttemptAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, grade.questionId())
                    .orElseThrow(() -> ApiException.badRequest("unknown_question",
                            "That question is not part of this attempt."));
            Question question = questions.get(grade.questionId());
            if (question == null) {
                continue;
            }

            // Clamp so a typo cannot award more than the question is worth.
            BigDecimal awarded = grade.pointsAwarded()
                    .max(BigDecimal.ZERO)
                    .min(question.getPoints());
            answer.setPointsAwarded(awarded);
            answer.setCorrect(awarded.compareTo(question.getPoints()) >= 0);
            answer.setFeedback(grade.feedback());
            answerRepository.save(answer);
        }

        List<AttemptAnswer> answers = answerRepository.findByAttempt(attemptId);
        boolean stillPending = answers.stream().anyMatch(answer -> answer.getCorrect() == null);

        BigDecimal score = answers.stream()
                .map(AttemptAnswer::getPointsAwarded)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO);

        attempt.setScore(score);
        attempt.setPercentage(percentageOf(score, attempt.getMaxScore()));
        attempt.setRequiresGrading(stillPending);

        if (!stillPending) {
            attempt.setStatus(Attempt.Status.GRADED);
            attempt.setGradedAt(Instant.now());
            attempt.setGradedBy(CurrentUser.requireId());
            attempt.setPassed(attempt.getPercentage().doubleValue() >= assessment.getPassingScore());
        }
        attemptRepository.save(attempt);

        if (attempt.isPassed()) {
            enrollmentService.onAssessmentPassed(assessment.getCourseId(), attempt.getUserId(),
                    assessment.getLessonId());
        }
        if (!stillPending) {
            notificationService.attemptGraded(attempt.getUserId(), assessment.getTitle(),
                    attempt.isPassed(), attempt.getPercentage().intValue());
            auditService.record(AuditService.ATTEMPT_GRADED, "Attempt", attemptId,
                    "Graded attempt for " + assessment.getTitle());
        }

        return result(attemptId);
    }

    // =================================================================
    // Results
    // =================================================================

    @Transactional(readOnly = true)
    public AssessmentDtos.AttemptResult result(UUID attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt", attemptId));

        AppUserPrincipal principal = CurrentUser.require();
        boolean privileged = principal.hasAnyRole(
                RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN, RoleCode.INSTRUCTOR);
        if (!privileged && !attempt.getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("You cannot view someone else's attempt.");
        }

        Assessment assessment = requireAssessment(attempt.getAssessmentId());
        List<AttemptAnswer> answers = answerRepository.findByAttempt(attemptId);
        Map<UUID, Question> questions = questionRepository
                .findAllById(answers.stream().map(AttemptAnswer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        Map<UUID, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestions(questions.keySet()).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        // The answer key is withheld unless the author chose to reveal it, or the
        // viewer is staff reviewing the attempt.
        boolean revealAnswers = assessment.isShowCorrectAnswers() || privileged;

        List<AssessmentDtos.ReviewedQuestion> review = answers.stream()
                .map(answer -> {
                    Question question = questions.get(answer.getQuestionId());
                    if (question == null) {
                        return null;
                    }
                    List<QuestionOption> options = optionsByQuestion
                            .getOrDefault(question.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(QuestionOption::getSortOrder))
                            .toList();

                    return new AssessmentDtos.ReviewedQuestion(
                            question.getId(), question.getPrompt(), question.getType(), question.getPoints(),
                            answer.getPointsAwarded(), answer.getCorrect(),
                            List.of(answer.getSelectedOptions()),
                            revealAnswers ? options.stream().filter(QuestionOption::isCorrect)
                                    .map(QuestionOption::getId).toList() : List.of(),
                            answer.getTextAnswer(),
                            revealAnswers ? question.getExplanation() : null,
                            answer.getFeedback(),
                            options.stream()
                                    .map(option -> new AssessmentDtos.OptionDetail(
                                            option.getId(), option.getLabel(),
                                            revealAnswers && option.isCorrect(), option.getSortOrder()))
                                    .toList());
                })
                .filter(Objects::nonNull)
                .toList();

        int used = attemptRepository.lastAttemptNumber(assessment.getId(), attempt.getUserId());
        int remaining = assessment.allowsUnlimitedAttempts()
                ? Integer.MAX_VALUE
                : Math.max(0, assessment.getMaxAttempts() - used);

        return new AssessmentDtos.AttemptResult(
                attempt.getId(), assessment.getId(), assessment.getTitle(), attempt.getStatus(),
                attempt.getScore(), attempt.getMaxScore(), attempt.getPercentage(), attempt.isPassed(),
                attempt.isRequiresGrading(), assessment.getPassingScore(), attempt.getAttemptNumber(),
                assessment.getMaxAttempts(), remaining, attempt.getSubmittedAt(),
                attempt.getTimeSpentSeconds(), review);
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.AttemptResult> myAttempts(UUID assessmentId) {
        return attemptRepository.findForUser(assessmentId, CurrentUser.requireId()).stream()
                .filter(attempt -> attempt.getStatus() != Attempt.Status.IN_PROGRESS)
                .map(attempt -> result(attempt.getId()))
                .toList();
    }

    // =================================================================
    // Mapping and guards
    // =================================================================

    private AssessmentDtos.AttemptView toAttemptView(Attempt attempt, Assessment assessment) {
        List<AttemptAnswer> answers = answerRepository.findByAttempt(attempt.getId());
        Map<UUID, Question> questions = questionRepository
                .findAllById(answers.stream().map(AttemptAnswer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        Map<UUID, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestions(questions.keySet()).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        List<AssessmentDtos.AttemptQuestion> attemptQuestions = answers.stream()
                .map(answer -> questions.get(answer.getQuestionId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Question::getSortOrder))
                .map(question -> {
                    // Only choice questions get an option list. A SHORT_ANSWER
                    // question stores its acceptable answers as options, so sending
                    // them would hand the learner the answer key.
                    if (!question.getType().isChoiceBased()) {
                        return new AssessmentDtos.AttemptQuestion(
                                question.getId(), question.getType(), question.getPrompt(),
                                question.getPoints(), List.of());
                    }

                    List<QuestionOption> options = new ArrayList<>(
                            optionsByQuestion.getOrDefault(question.getId(), List.of()));
                    options.sort(Comparator.comparing(QuestionOption::getSortOrder));
                    if (assessment.isShuffleOptions()) {
                        Collections.shuffle(options);
                    }
                    return new AssessmentDtos.AttemptQuestion(
                            question.getId(), question.getType(), question.getPrompt(), question.getPoints(),
                            options.stream()
                                    .map(option -> new AssessmentDtos.AttemptOption(
                                            option.getId(), option.getLabel()))
                                    .toList());
                })
                .toList();

        List<AssessmentDtos.SavedAnswer> saved = answers.stream()
                .filter(AttemptService::isAnswered)
                .map(answer -> new AssessmentDtos.SavedAnswer(
                        answer.getQuestionId(), List.of(answer.getSelectedOptions()), answer.getTextAnswer()))
                .toList();

        return new AssessmentDtos.AttemptView(
                attempt.getId(), assessment.getId(), assessment.getTitle(), assessment.getDescription(),
                attempt.getAttemptNumber(), assessment.getMaxAttempts(), assessment.getTimeLimitMinutes(),
                attempt.getStartedAt(), attempt.getExpiresAt(), assessment.getPassingScore(),
                attemptQuestions, saved);
    }

    private BigDecimal totalPointsFor(UUID attemptId) {
        return answerRepository.findByAttempt(attemptId).stream()
                .map(answer -> questionRepository.findById(answer.getQuestionId())
                        .map(Question::getPoints).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Assessment requireAssessment(UUID assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> ApiException.notFound("Assessment", assessmentId));
    }

    private Attempt requireOwnAttempt(UUID attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt", attemptId));
        if (!attempt.getUserId().equals(CurrentUser.requireId())) {
            throw ApiException.forbidden("This attempt belongs to someone else.");
        }
        return attempt;
    }

    private void assertOpen(Attempt attempt) {
        if (attempt.getStatus() != Attempt.Status.IN_PROGRESS) {
            throw ApiException.conflict("attempt_closed", "This attempt has already been submitted.");
        }
        if (attempt.isTimedOut()) {
            throw ApiException.conflict("attempt_expired", "Your time for this attempt has run out.");
        }
    }

    private void assertCanGrade() {
        if (!CurrentUser.require().hasAnyRole(
                RoleCode.INSTRUCTOR, RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN)) {
            throw ApiException.forbidden("Only instructors and admins can grade submissions.");
        }
    }
}
