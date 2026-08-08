package com.learnnexus.assessment;

import com.learnnexus.audit.AuditService;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.catalog.Course;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Authoring side of assessments: definitions, questions and options. */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepositories.AssessmentRepository assessmentRepository;
    private final AssessmentRepositories.QuestionRepository questionRepository;
    private final AssessmentRepositories.QuestionOptionRepository optionRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final CatalogRepositories.LessonRepository lessonRepository;
    private final com.learnnexus.catalog.CourseService courseService;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;

    @Transactional(readOnly = true)
    public List<AssessmentDtos.AssessmentSummary> listForCourse(UUID courseId) {
        return assessmentRepository.findByCourse(courseId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.AssessmentDetail get(UUID assessmentId) {
        Assessment assessment = require(assessmentId);
        return new AssessmentDtos.AssessmentDetail(toSummary(assessment), questionsOf(assessmentId));
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail create(AssessmentDtos.AssessmentRequest request) {
        Course course = requireEditableCourse(request.courseId());
        validateLesson(request.lessonId(), course.getId());

        Assessment assessment = new Assessment();
        assessment.setCourseId(course.getId());
        apply(assessment, request);
        assessmentRepository.save(assessment);

        auditService.record(AuditService.COURSE_UPDATED, "Assessment", assessment.getId(),
                "Created assessment " + assessment.getTitle());
        return get(assessment.getId());
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail update(UUID assessmentId, AssessmentDtos.AssessmentRequest request) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());
        validateLesson(request.lessonId(), assessment.getCourseId());

        apply(assessment, request);
        assessment.setUpdatedAt(Instant.now());
        assessmentRepository.save(assessment);
        return get(assessmentId);
    }

    private void apply(Assessment assessment, AssessmentDtos.AssessmentRequest request) {
        assessment.setLessonId(request.lessonId());
        assessment.setTitle(request.title().trim());
        assessment.setDescription(blankToNull(request.description()));
        assessment.setType(request.type() == null ? Assessment.Type.QUIZ : request.type());
        assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        assessment.setMaxAttempts((short) request.maxAttempts());
        assessment.setPassingScore((short) request.passingScore());
        assessment.setQuestionsPerAttempt(request.questionsPerAttempt());
        assessment.setShuffleQuestions(request.shuffleQuestions());
        assessment.setShuffleOptions(request.shuffleOptions());
        assessment.setNegativeMarking(request.negativeMarking() == null
                ? BigDecimal.ZERO : request.negativeMarking());
        assessment.setShowCorrectAnswers(request.showCorrectAnswers());
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail changeStatus(UUID assessmentId, Assessment.Status status) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        if (status == Assessment.Status.PUBLISHED) {
            long questions = questionRepository.countByAssessment(assessmentId);
            if (questions == 0) {
                throw ApiException.badRequest("assessment_empty",
                        "Add at least one question before publishing this assessment.");
            }
            assertEveryChoiceQuestionHasAnAnswer(assessmentId);
        }

        assessment.setStatus(status);
        assessment.setUpdatedAt(Instant.now());
        assessmentRepository.save(assessment);
        return get(assessmentId);
    }

    /**
     * A choice question with no correct option can never be passed, which would
     * silently trap learners. Caught at publish time rather than at grading time.
     */
    private void assertEveryChoiceQuestionHasAnAnswer(UUID assessmentId) {
        List<Question> questions = questionRepository.findByAssessment(assessmentId);
        Map<UUID, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestions(questions.stream().map(Question::getId).toList()).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        for (Question question : questions) {
            if (!question.getType().isChoiceBased()) {
                continue;
            }
            List<QuestionOption> options = optionsByQuestion.getOrDefault(question.getId(), List.of());
            if (options.size() < 2) {
                throw ApiException.badRequest("question_incomplete",
                        "\"" + truncate(question.getPrompt()) + "\" needs at least two options.");
            }
            if (options.stream().noneMatch(QuestionOption::isCorrect)) {
                throw ApiException.badRequest("question_incomplete",
                        "\"" + truncate(question.getPrompt()) + "\" has no correct answer marked.");
            }
        }
    }

    @Transactional
    public void delete(UUID assessmentId) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        long attempts = jdbc.queryForLong(
                "select count(*) from attempts where tenant_id = ? and assessment_id = ?", assessmentId);
        if (attempts > 0) {
            throw ApiException.conflict("assessment_in_use",
                    "Learners have already taken this assessment. Archive it instead.");
        }
        assessmentRepository.delete(assessment);
    }

    // ---------------- Questions ----------------

    @Transactional
    public AssessmentDtos.AssessmentDetail addQuestion(UUID assessmentId, AssessmentDtos.QuestionRequest request) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        Question question = new Question();
        question.setAssessmentId(assessmentId);
        question.setSortOrder((short) (questionRepository.maxSortOrder(assessmentId) + 1));
        applyQuestion(question, request);
        questionRepository.save(question);
        replaceOptions(question, request);

        return get(assessmentId);
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail updateQuestion(UUID assessmentId, UUID questionId,
                                                          AssessmentDtos.QuestionRequest request) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        Question question = questionRepository.findById(questionId)
                .filter(candidate -> candidate.getAssessmentId().equals(assessmentId))
                .orElseThrow(() -> ApiException.notFound("Question", questionId));

        applyQuestion(question, request);
        questionRepository.save(question);
        replaceOptions(question, request);

        return get(assessmentId);
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail deleteQuestion(UUID assessmentId, UUID questionId) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        questionRepository.findById(questionId)
                .filter(candidate -> candidate.getAssessmentId().equals(assessmentId))
                .ifPresent(questionRepository::delete);

        return get(assessmentId);
    }

    @Transactional
    public AssessmentDtos.AssessmentDetail reorderQuestions(UUID assessmentId, List<UUID> orderedIds) {
        Assessment assessment = require(assessmentId);
        requireEditableCourse(assessment.getCourseId());

        Map<UUID, Question> byId = questionRepository.findByAssessment(assessmentId).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        short position = 0;
        for (UUID id : orderedIds) {
            Question question = byId.get(id);
            if (question != null) {
                question.setSortOrder(position++);
                questionRepository.save(question);
            }
        }
        return get(assessmentId);
    }

    private void applyQuestion(Question question, AssessmentDtos.QuestionRequest request) {
        question.setType(request.type() == null ? Question.Type.SINGLE_CHOICE : request.type());
        question.setPrompt(request.prompt().trim());
        question.setExplanation(blankToNull(request.explanation()));
        question.setPoints(request.points() == null || request.points().signum() <= 0
                ? BigDecimal.ONE : request.points());
        question.setDifficulty(request.difficulty() == null ? Question.Difficulty.MEDIUM : request.difficulty());
    }

    private void replaceOptions(Question question, AssessmentDtos.QuestionRequest request) {
        optionRepository.deleteByQuestionId(question.getId());
        if (!question.getType().isChoiceBased() || request.options() == null) {
            return;
        }

        boolean singleAnswer = question.getType() != Question.Type.MULTI_CHOICE;
        boolean correctSeen = false;
        short order = 0;

        for (AssessmentDtos.OptionRequest optionRequest : request.options()) {
            if (optionRequest.label() == null || optionRequest.label().isBlank()) {
                continue;
            }
            QuestionOption option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setLabel(optionRequest.label().trim());
            // Single-answer questions keep only the first option flagged correct,
            // so a mis-edited question cannot become unpassable.
            boolean correct = optionRequest.correct() && !(singleAnswer && correctSeen);
            option.setCorrect(correct);
            correctSeen = correctSeen || correct;
            option.setSortOrder(order++);
            optionRepository.save(option);
        }
    }

    // ---------------- Mapping ----------------

    List<AssessmentDtos.QuestionDetail> questionsOf(UUID assessmentId) {
        List<Question> questions = questionRepository.findByAssessment(assessmentId);
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<QuestionOption>> optionsByQuestion = optionRepository
                .findByQuestions(questions.stream().map(Question::getId).toList()).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        return questions.stream()
                .map(question -> new AssessmentDtos.QuestionDetail(
                        question.getId(), question.getType(), question.getPrompt(), question.getExplanation(),
                        question.getPoints(), question.getDifficulty(), question.getSortOrder(),
                        optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(QuestionOption::getSortOrder))
                                .map(option -> new AssessmentDtos.OptionDetail(
                                        option.getId(), option.getLabel(), option.isCorrect(), option.getSortOrder()))
                                .toList()))
                .toList();
    }

    AssessmentDtos.AssessmentSummary toSummary(Assessment assessment) {
        List<Question> questions = questionRepository.findByAssessment(assessment.getId());
        BigDecimal totalPoints = questions.stream()
                .map(Question::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var stats = jdbc.queryOne("""
                select count(*) filter (where status in ('SUBMITTED','GRADED'))          as attempts,
                       round(avg(percentage) filter (where status in ('SUBMITTED','GRADED'))) as avg_score,
                       count(*) filter (where requires_grading and status = 'SUBMITTED') as pending
                from attempts
                where tenant_id = ? and assessment_id = ?
                """, (rs, rowNum) -> {
            long attempts = rs.getLong("attempts");
            int avg = rs.getInt("avg_score");
            boolean avgNull = rs.wasNull();
            return new long[]{attempts, avgNull ? -1 : avg, rs.getLong("pending")};
        }, assessment.getId()).orElse(new long[]{0, -1, 0});

        return new AssessmentDtos.AssessmentSummary(
                assessment.getId(), assessment.getCourseId(), assessment.getLessonId(), assessment.getTitle(),
                assessment.getDescription(), assessment.getType(), assessment.getStatus(),
                assessment.getTimeLimitMinutes(), assessment.getMaxAttempts(), assessment.getPassingScore(),
                assessment.getQuestionsPerAttempt(), assessment.isShuffleQuestions(), assessment.isShuffleOptions(),
                assessment.getNegativeMarking(), assessment.isShowCorrectAnswers(),
                questions.size(), totalPoints, stats[0],
                stats[1] < 0 ? null : (int) stats[1], stats[2]);
    }

    // ---------------- Guards ----------------

    Assessment require(UUID assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> ApiException.notFound("Assessment", assessmentId));
    }

    private Course requireEditableCourse(UUID courseId) {
        Course course = courseRepository.findActiveById(courseId)
                .orElseThrow(() -> ApiException.notFound("Course", courseId));
        courseService.assertCanEdit(course);
        return course;
    }

    private void validateLesson(UUID lessonId, UUID courseId) {
        if (lessonId == null) {
            return;
        }
        lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.badRequest("unknown_lesson",
                        "That lesson does not belong to this course."));
    }

    private static String truncate(String value) {
        return value.length() <= 60 ? value : value.substring(0, 60) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    UUID currentUserId() {
        return CurrentUser.requireId();
    }
}
