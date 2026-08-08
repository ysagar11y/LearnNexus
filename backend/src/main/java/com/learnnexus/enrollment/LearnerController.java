package com.learnnexus.enrollment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Everything a learner does with their own courses. */
@Tag(name = "My learning", description = "The learner's own dashboard, courses and progress.")
@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
public class LearnerController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "Dashboard figures, continue-learning list and upcoming sessions")
    @GetMapping("/dashboard")
    public EnrollmentDtos.LearnerDashboard dashboard() {
        return enrollmentService.dashboard();
    }

    @Operation(summary = "The learner's enrolments, optionally filtered by status")
    @GetMapping("/learning")
    public List<EnrollmentDtos.EnrollmentSummary> myLearning(
            @RequestParam(required = false) Enrollment.Status status) {
        return enrollmentService.myLearning(status);
    }

    @Operation(summary = "Open a course in the player, with per-lesson progress")
    @GetMapping("/courses/{courseId}")
    public EnrollmentDtos.PlayerView player(@PathVariable UUID courseId) {
        return enrollmentService.player(courseId);
    }

    @Operation(summary = "Record playback position or mark a lesson complete")
    @PostMapping("/courses/{courseId}/progress")
    public EnrollmentDtos.ProgressResponse recordProgress(
            @PathVariable UUID courseId,
            @Valid @RequestBody EnrollmentDtos.ProgressUpdateRequest request) {
        return enrollmentService.recordProgress(courseId, request);
    }

    @Operation(summary = "Join a course that is open for self-enrolment")
    @PostMapping("/courses/{courseId}/enroll")
    public EnrollmentDtos.EnrollmentSummary selfEnroll(@PathVariable UUID courseId) {
        return enrollmentService.selfEnroll(courseId);
    }
}
