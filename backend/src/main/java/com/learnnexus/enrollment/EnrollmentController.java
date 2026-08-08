package com.learnnexus.enrollment;

import com.learnnexus.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Enrolments", description = "Assigning courses and tracking who is on them.")
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private static final String CAN_ASSIGN = "hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','INSTRUCTOR','MANAGER')";

    private final EnrollmentService enrollmentService;

    @Operation(summary = "Search enrolments by course, learner or status")
    @GetMapping
    @PreAuthorize(CAN_ASSIGN)
    public PageResponse<EnrollmentDtos.EnrollmentSummary> search(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Enrollment.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return enrollmentService.search(courseId, userId, status, page, size);
    }

    @Operation(summary = "Assign a course to specific learners")
    @PostMapping
    @PreAuthorize(CAN_ASSIGN)
    public EnrollmentDtos.BulkResult enroll(@Valid @RequestBody EnrollmentDtos.EnrollRequest request) {
        return enrollmentService.enroll(request);
    }

    @Operation(summary = "Assign a course to everyone in an organisation unit")
    @PostMapping("/by-org-unit")
    @PreAuthorize(CAN_ASSIGN)
    public EnrollmentDtos.BulkResult enrollOrgUnit(
            @Valid @RequestBody EnrollmentDtos.EnrollByOrgUnitRequest request) {
        return enrollmentService.enrollOrgUnit(request);
    }

    @Operation(summary = "Change an enrolment's due date")
    @PatchMapping("/{enrollmentId}/due-date")
    @PreAuthorize(CAN_ASSIGN)
    public EnrollmentDtos.EnrollmentSummary setDueDate(@PathVariable UUID enrollmentId,
                                                       @RequestBody EnrollmentDtos.DueDateRequest request) {
        return enrollmentService.setDueDate(enrollmentId, request.dueAt());
    }

    @Operation(summary = "Withdraw an enrolment that has not been completed")
    @DeleteMapping("/{enrollmentId}")
    @PreAuthorize(CAN_ASSIGN)
    public ResponseEntity<Void> withdraw(@PathVariable UUID enrollmentId) {
        enrollmentService.withdraw(enrollmentId);
        return ResponseEntity.noContent().build();
    }
}
