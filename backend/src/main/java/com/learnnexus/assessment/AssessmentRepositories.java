package com.learnnexus.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AssessmentRepositories {

    private AssessmentRepositories() {
    }

    public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

        @Query("select a from Assessment a where a.courseId = :courseId order by a.createdAt")
        List<Assessment> findByCourse(@Param("courseId") UUID courseId);

        Optional<Assessment> findByLessonId(UUID lessonId);
    }

    public interface QuestionRepository extends JpaRepository<Question, UUID> {

        @Query("select q from Question q where q.assessmentId = :assessmentId order by q.sortOrder, q.createdAt")
        List<Question> findByAssessment(@Param("assessmentId") UUID assessmentId);

        @Query("select count(q) from Question q where q.assessmentId = :assessmentId")
        long countByAssessment(@Param("assessmentId") UUID assessmentId);

        @Query("select coalesce(max(q.sortOrder), -1) from Question q where q.assessmentId = :assessmentId")
        short maxSortOrder(@Param("assessmentId") UUID assessmentId);
    }

    public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

        @Query("select o from QuestionOption o where o.questionId = :questionId order by o.sortOrder")
        List<QuestionOption> findByQuestion(@Param("questionId") UUID questionId);

        @Query("select o from QuestionOption o where o.questionId in :questionIds order by o.sortOrder")
        List<QuestionOption> findByQuestions(@Param("questionIds") Collection<UUID> questionIds);

        void deleteByQuestionId(UUID questionId);
    }

    public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

        @Query("""
                select a from Attempt a
                where a.assessmentId = :assessmentId and a.userId = :userId
                order by a.attemptNumber desc
                """)
        List<Attempt> findForUser(@Param("assessmentId") UUID assessmentId, @Param("userId") UUID userId);

        @Query("""
                select a from Attempt a
                where a.assessmentId = :assessmentId and a.userId = :userId and a.status = 'IN_PROGRESS'
                """)
        Optional<Attempt> findInProgress(@Param("assessmentId") UUID assessmentId, @Param("userId") UUID userId);

        @Query("select coalesce(max(a.attemptNumber), 0) from Attempt a where a.assessmentId = :assessmentId and a.userId = :userId")
        short lastAttemptNumber(@Param("assessmentId") UUID assessmentId, @Param("userId") UUID userId);

        @Query("""
                select a from Attempt a
                where a.requiresGrading = true and a.status = 'SUBMITTED'
                  and (:assessmentId is null or a.assessmentId = :assessmentId)
                order by a.submittedAt
                """)
        Page<Attempt> findAwaitingGrading(@Param("assessmentId") UUID assessmentId, Pageable pageable);

        @Query("select a from Attempt a where a.assessmentId = :assessmentId order by a.submittedAt desc")
        Page<Attempt> findByAssessment(@Param("assessmentId") UUID assessmentId, Pageable pageable);
    }

    public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

        @Query("select a from AttemptAnswer a where a.attemptId = :attemptId")
        List<AttemptAnswer> findByAttempt(@Param("attemptId") UUID attemptId);

        Optional<AttemptAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
    }
}
