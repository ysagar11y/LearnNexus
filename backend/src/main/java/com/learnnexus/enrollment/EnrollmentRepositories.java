package com.learnnexus.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EnrollmentRepositories {

    private EnrollmentRepositories() {
    }

    public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

        Optional<Enrollment> findByCourseIdAndUserId(UUID courseId, UUID userId);

        @Query("select e from Enrollment e where e.userId = :userId order by e.lastAccessedAt desc nulls last, e.enrolledAt desc")
        List<Enrollment> findForLearner(@Param("userId") UUID userId);

        @Query("""
                select e from Enrollment e
                where e.userId = :userId
                  and (:status is null or e.status = :status)
                order by e.lastAccessedAt desc nulls last, e.enrolledAt desc
                """)
        List<Enrollment> findForLearnerByStatus(@Param("userId") UUID userId,
                                                @Param("status") Enrollment.Status status);

        @Query("""
                select e from Enrollment e
                where (:courseId is null or e.courseId = :courseId)
                  and (:userId is null or e.userId = :userId)
                  and (:status is null or e.status = :status)
                """)
        Page<Enrollment> search(@Param("courseId") UUID courseId,
                                @Param("userId") UUID userId,
                                @Param("status") Enrollment.Status status,
                                Pageable pageable);

        @Query("select e from Enrollment e where e.courseId = :courseId")
        List<Enrollment> findByCourse(@Param("courseId") UUID courseId);

        @Query("select count(e) from Enrollment e where e.courseId = :courseId and e.status <> 'WITHDRAWN'")
        long countActiveForCourse(@Param("courseId") UUID courseId);

        @Query("select e.courseId from Enrollment e where e.userId = :userId and e.status = 'COMPLETED'")
        List<UUID> findCompletedCourseIds(@Param("userId") UUID userId);

        @Query("select e from Enrollment e where e.userId = :userId and e.courseId in :courseIds")
        List<Enrollment> findForLearnerInCourses(@Param("userId") UUID userId,
                                                 @Param("courseIds") Collection<UUID> courseIds);

        /** Active enrolments falling due inside a window; drives reminder emails. */
        @Query("""
                select e from Enrollment e
                where e.status = 'ACTIVE' and e.dueAt is not null
                  and e.dueAt between :from and :to
                """)
        List<Enrollment> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);

        @Query("select count(e) from Enrollment e where e.status = :status")
        long countByStatus(@Param("status") Enrollment.Status status);
    }

    public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

        Optional<LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

        @Query("select p from LessonProgress p where p.enrollmentId = :enrollmentId")
        List<LessonProgress> findByEnrollment(@Param("enrollmentId") UUID enrollmentId);

        @Query("select count(p) from LessonProgress p where p.enrollmentId = :enrollmentId and p.status = 'COMPLETED'")
        long countCompleted(@Param("enrollmentId") UUID enrollmentId);

        @Query("select coalesce(sum(p.secondsWatched), 0) from LessonProgress p where p.userId = :userId")
        long totalSecondsWatched(@Param("userId") UUID userId);
    }
}
