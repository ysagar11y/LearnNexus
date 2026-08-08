package com.learnnexus.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------

    public record CategoryResponse(
            UUID id,
            String name,
            String slug,
            String description,
            String color,
            int sortOrder,
            long courseCount
    ) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 400) String description,
            @Size(max = 16) String color
    ) {}

    // ---------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------

    public record CourseSummary(
            UUID id,
            String code,
            String title,
            String slug,
            String summary,
            String thumbnailUrl,
            Course.Level level,
            Course.DeliveryType deliveryType,
            Course.Status status,
            Course.EnrollmentMode enrollmentMode,
            UUID categoryId,
            String categoryName,
            String categoryColor,
            List<String> tags,
            int estimatedMinutes,
            int lessonCount,
            boolean mandatory,
            boolean certificateEnabled,
            String ownerName,
            long enrolledCount,
            Integer averageProgress,
            Instant publishedAt,
            Instant updatedAt
    ) {}

    public record CourseDetail(
            CourseSummary summary,
            String description,
            String language,
            int version,
            Integer seatLimit,
            int passingScore,
            UUID ownerId,
            UUID certificateTemplateId,
            List<UUID> prerequisiteIds,
            List<PrerequisiteRef> prerequisites,
            List<InstructorRef> instructors,
            List<ModuleDetail> modules,
            CourseStats stats
    ) {}

    public record PrerequisiteRef(UUID id, String title, Course.Status status) {}

    public record InstructorRef(UUID id, String name, String email, String avatarUrl) {}

    public record CourseStats(
            long enrolled,
            long completed,
            long inProgress,
            long overdue,
            int averageProgress,
            long certificatesIssued,
            Integer averageScore
    ) {}

    public record CourseRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 40) String code,
            @Size(max = 600) String summary,
            String description,
            String thumbnailUrl,
            UUID categoryId,
            UUID ownerId,
            Course.Level level,
            Course.DeliveryType deliveryType,
            Course.EnrollmentMode enrollmentMode,
            @Size(max = 16) String language,
            @Min(0) @Max(100000) int estimatedMinutes,
            Integer seatLimit,
            @Min(0) @Max(100) int passingScore,
            boolean mandatory,
            boolean certificateEnabled,
            UUID certificateTemplateId,
            List<String> tags,
            List<UUID> prerequisiteIds,
            List<UUID> instructorIds
    ) {}

    public record StatusRequest(Course.Status status) {}

    // ---------------------------------------------------------------
    // Modules and lessons
    // ---------------------------------------------------------------

    public record ModuleDetail(
            UUID id,
            String title,
            String summary,
            int sortOrder,
            List<LessonDetail> lessons
    ) {}

    public record ModuleRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 600) String summary
    ) {}

    public record LessonDetail(
            UUID id,
            UUID moduleId,
            String title,
            Lesson.ContentType contentType,
            String contentUrl,
            String contentHtml,
            UUID assetId,
            int durationSeconds,
            int sortOrder,
            boolean preview,
            boolean mandatory,
            UUID assessmentId
    ) {}

    public record LessonRequest(
            @NotBlank @Size(max = 240) String title,
            Lesson.ContentType contentType,
            String contentUrl,
            String contentHtml,
            UUID assetId,
            @Min(0) int durationSeconds,
            boolean preview,
            boolean mandatory
    ) {}

    /** Payload for drag-and-drop reordering: ids in their new order. */
    public record ReorderRequest(List<UUID> orderedIds) {}

    public record MoveLessonRequest(UUID targetModuleId, int position) {}
}
