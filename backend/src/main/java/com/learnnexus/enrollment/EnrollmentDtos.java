package com.learnnexus.enrollment;

import com.learnnexus.catalog.Course;
import com.learnnexus.catalog.Lesson;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EnrollmentDtos {

    private EnrollmentDtos() {
    }

    public record EnrollmentSummary(
            UUID id,
            UUID courseId,
            String courseTitle,
            String courseSlug,
            String thumbnailUrl,
            String categoryName,
            Course.DeliveryType deliveryType,
            UUID userId,
            String learnerName,
            String learnerEmail,
            Enrollment.Status status,
            Enrollment.Source source,
            int progressPercent,
            int lessonsCompleted,
            int lessonCount,
            int estimatedMinutes,
            boolean mandatory,
            boolean overdue,
            Instant dueAt,
            Instant completedAt,
            Instant lastAccessedAt,
            Instant enrolledAt
    ) {}

    /** The learner's view of a course while taking it. */
    public record PlayerView(
            UUID enrollmentId,
            UUID courseId,
            String courseTitle,
            String courseSummary,
            String description,
            Course.DeliveryType deliveryType,
            int progressPercent,
            Enrollment.Status status,
            Instant dueAt,
            boolean overdue,
            boolean certificateEnabled,
            UUID certificateId,
            List<ModuleView> modules,
            UUID nextLessonId
    ) {}

    public record ModuleView(
            UUID id,
            String title,
            String summary,
            int sortOrder,
            List<LessonView> lessons
    ) {}

    public record LessonView(
            UUID id,
            String title,
            Lesson.ContentType contentType,
            String contentUrl,
            String contentHtml,
            UUID assetId,
            int durationSeconds,
            boolean mandatory,
            boolean preview,
            LessonProgress.Status status,
            int lastPositionSeconds,
            int secondsWatched,
            UUID assessmentId,
            boolean assessmentPassed,
            Integer assessmentScore,
            boolean locked
    ) {}

    public record EnrollRequest(
            @NotNull UUID courseId,
            @NotEmpty List<UUID> userIds,
            Instant dueAt,
            Enrollment.Source source,
            boolean notifyLearners
    ) {}

    public record EnrollByOrgUnitRequest(
            @NotNull UUID courseId,
            @NotNull UUID orgUnitId,
            boolean includeSubtree,
            Instant dueAt,
            boolean notifyLearners
    ) {}

    public record BulkResult(
            int enrolled,
            int alreadyEnrolled,
            int skipped,
            List<String> messages
    ) {}

    public record ProgressUpdateRequest(
            @NotNull UUID lessonId,
            int positionSeconds,
            int watchedSeconds,
            Boolean completed
    ) {}

    public record ProgressResponse(
            UUID lessonId,
            LessonProgress.Status status,
            int progressPercent,
            Enrollment.Status enrollmentStatus,
            boolean courseCompleted,
            UUID certificateId,
            UUID nextLessonId
    ) {}

    public record DueDateRequest(Instant dueAt) {}

    /** Aggregate figures for the learner's home screen. */
    public record LearnerDashboard(
            int assigned,
            int inProgress,
            int completed,
            int overdue,
            int certificates,
            long learningMinutes,
            int currentStreakDays,
            List<EnrollmentSummary> continueLearning,
            List<EnrollmentSummary> dueSoon,
            List<UpcomingSession> upcomingSessions
    ) {}

    public record UpcomingSession(
            UUID id,
            UUID courseId,
            String courseTitle,
            String title,
            String provider,
            String joinUrl,
            Instant startsAt,
            Instant endsAt
    ) {}
}
