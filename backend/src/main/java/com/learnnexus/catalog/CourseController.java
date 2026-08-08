package com.learnnexus.catalog;

import com.learnnexus.common.PageResponse;
import com.learnnexus.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Courses", description = "The content library: courses, sections and lessons.")
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final String CAN_AUTHOR = "hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR','INSTRUCTOR')";

    private final CourseService courseService;

    @Operation(summary = "Search the content library")
    @GetMapping
    public PageResponse<CatalogDtos.CourseSummary> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Course.Status status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Course.Level level,
            @RequestParam(required = false) Course.DeliveryType deliveryType,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String sort) {
        return courseService.search(query, status, categoryId, level, deliveryType, tag, page, size, sort);
    }

    @Operation(summary = "Courses the signed-in instructor owns or teaches")
    @GetMapping("/teaching")
    @PreAuthorize(CAN_AUTHOR)
    public List<CatalogDtos.CourseSummary> teaching() {
        return courseService.coursesForInstructor(CurrentUser.requireId());
    }

    @Operation(summary = "Full course structure including sections and lessons")
    @GetMapping("/{courseId}")
    public CatalogDtos.CourseDetail get(@PathVariable UUID courseId) {
        return courseService.get(courseId);
    }

    @Operation(summary = "Aggregate progress and completion figures for a course")
    @GetMapping("/{courseId}/stats")
    @PreAuthorize(CAN_AUTHOR + " or hasRole('MANAGER')")
    public CatalogDtos.CourseStats stats(@PathVariable UUID courseId) {
        return courseService.statsFor(courseId);
    }

    @Operation(summary = "Create a course")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail create(@Valid @RequestBody CatalogDtos.CourseRequest request) {
        return courseService.create(request);
    }

    @Operation(summary = "Update a course")
    @PutMapping("/{courseId}")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail update(@PathVariable UUID courseId,
                                           @Valid @RequestBody CatalogDtos.CourseRequest request) {
        return courseService.update(courseId, request);
    }

    @Operation(summary = "Move a course through draft, review, published or archived")
    @PatchMapping("/{courseId}/status")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail changeStatus(@PathVariable UUID courseId,
                                                 @RequestBody CatalogDtos.StatusRequest request) {
        return courseService.changeStatus(courseId, request.status());
    }

    @Operation(summary = "Delete a course that has no enrolments")
    @DeleteMapping("/{courseId}")
    @PreAuthorize(CAN_AUTHOR)
    public ResponseEntity<Void> delete(@PathVariable UUID courseId) {
        courseService.delete(courseId);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Sections ----------------

    @Operation(summary = "Add a section")
    @PostMapping("/{courseId}/modules")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail addModule(@PathVariable UUID courseId,
                                              @Valid @RequestBody CatalogDtos.ModuleRequest request) {
        return courseService.addModule(courseId, request);
    }

    @Operation(summary = "Rename a section")
    @PutMapping("/{courseId}/modules/{moduleId}")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail updateModule(@PathVariable UUID courseId, @PathVariable UUID moduleId,
                                                 @Valid @RequestBody CatalogDtos.ModuleRequest request) {
        return courseService.updateModule(courseId, moduleId, request);
    }

    @Operation(summary = "Delete a section and its lessons")
    @DeleteMapping("/{courseId}/modules/{moduleId}")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail deleteModule(@PathVariable UUID courseId, @PathVariable UUID moduleId) {
        return courseService.deleteModule(courseId, moduleId);
    }

    @Operation(summary = "Reorder sections")
    @PutMapping("/{courseId}/modules/order")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail reorderModules(@PathVariable UUID courseId,
                                                   @RequestBody CatalogDtos.ReorderRequest request) {
        return courseService.reorderModules(courseId, request.orderedIds());
    }

    // ---------------- Lessons ----------------

    @Operation(summary = "Add a lesson to a section")
    @PostMapping("/{courseId}/modules/{moduleId}/lessons")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail addLesson(@PathVariable UUID courseId, @PathVariable UUID moduleId,
                                              @Valid @RequestBody CatalogDtos.LessonRequest request) {
        return courseService.addLesson(courseId, moduleId, request);
    }

    @Operation(summary = "Update a lesson")
    @PutMapping("/{courseId}/lessons/{lessonId}")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail updateLesson(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                                                 @Valid @RequestBody CatalogDtos.LessonRequest request) {
        return courseService.updateLesson(courseId, lessonId, request);
    }

    @Operation(summary = "Delete a lesson")
    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail deleteLesson(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return courseService.deleteLesson(courseId, lessonId);
    }

    @Operation(summary = "Move a lesson to another section or position")
    @PutMapping("/{courseId}/lessons/{lessonId}/move")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail moveLesson(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                                               @RequestBody CatalogDtos.MoveLessonRequest request) {
        return courseService.moveLesson(courseId, lessonId, request.targetModuleId(), request.position());
    }

    @Operation(summary = "Reorder lessons within a section")
    @PutMapping("/{courseId}/modules/{moduleId}/lessons/order")
    @PreAuthorize(CAN_AUTHOR)
    public CatalogDtos.CourseDetail reorderLessons(@PathVariable UUID courseId, @PathVariable UUID moduleId,
                                                   @RequestBody CatalogDtos.ReorderRequest request) {
        return courseService.reorderLessons(courseId, moduleId, request.orderedIds());
    }
}
