package com.learnnexus.assessment;

import com.learnnexus.common.PageResponse;
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

@Tag(name = "Assessments", description = "Quiz and exam authoring, taking and grading.")
@RestController
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private static final String CAN_AUTHOR = "hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR','INSTRUCTOR')";

    private final AssessmentService assessmentService;
    private final AttemptService attemptService;

    // ---------------- Authoring ----------------

    @Operation(summary = "Assessments belonging to a course")
    @GetMapping
    @PreAuthorize(CAN_AUTHOR)
    public List<AssessmentDtos.AssessmentSummary> listForCourse(@RequestParam UUID courseId) {
        return assessmentService.listForCourse(courseId);
    }

    @Operation(summary = "An assessment with its full question set and answer key")
    @GetMapping("/{assessmentId}")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail get(@PathVariable UUID assessmentId) {
        return assessmentService.get(assessmentId);
    }

    @Operation(summary = "Create an assessment")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail create(@Valid @RequestBody AssessmentDtos.AssessmentRequest request) {
        return assessmentService.create(request);
    }

    @Operation(summary = "Update an assessment's settings")
    @PutMapping("/{assessmentId}")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail update(@PathVariable UUID assessmentId,
                                                  @Valid @RequestBody AssessmentDtos.AssessmentRequest request) {
        return assessmentService.update(assessmentId, request);
    }

    @Operation(summary = "Publish or archive an assessment")
    @PatchMapping("/{assessmentId}/status")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail changeStatus(@PathVariable UUID assessmentId,
                                                        @RequestBody AssessmentDtos.StatusRequest request) {
        return assessmentService.changeStatus(assessmentId, request.status());
    }

    @Operation(summary = "Delete an assessment nobody has attempted")
    @DeleteMapping("/{assessmentId}")
    @PreAuthorize(CAN_AUTHOR)
    public ResponseEntity<Void> delete(@PathVariable UUID assessmentId) {
        assessmentService.delete(assessmentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a question")
    @PostMapping("/{assessmentId}/questions")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail addQuestion(@PathVariable UUID assessmentId,
                                                       @Valid @RequestBody AssessmentDtos.QuestionRequest request) {
        return assessmentService.addQuestion(assessmentId, request);
    }

    @Operation(summary = "Update a question and its options")
    @PutMapping("/{assessmentId}/questions/{questionId}")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail updateQuestion(@PathVariable UUID assessmentId,
                                                          @PathVariable UUID questionId,
                                                          @Valid @RequestBody AssessmentDtos.QuestionRequest request) {
        return assessmentService.updateQuestion(assessmentId, questionId, request);
    }

    @Operation(summary = "Delete a question")
    @DeleteMapping("/{assessmentId}/questions/{questionId}")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail deleteQuestion(@PathVariable UUID assessmentId,
                                                          @PathVariable UUID questionId) {
        return assessmentService.deleteQuestion(assessmentId, questionId);
    }

    @Operation(summary = "Reorder questions")
    @PutMapping("/{assessmentId}/questions/order")
    @PreAuthorize(CAN_AUTHOR)
    public AssessmentDtos.AssessmentDetail reorderQuestions(
            @PathVariable UUID assessmentId,
            @RequestBody com.learnnexus.catalog.CatalogDtos.ReorderRequest request) {
        return assessmentService.reorderQuestions(assessmentId, request.orderedIds());
    }

    // ---------------- Taking ----------------

    @Operation(summary = "Start a new attempt, or resume one that is still open")
    @PostMapping("/{assessmentId}/attempts")
    public AssessmentDtos.AttemptView start(@PathVariable UUID assessmentId) {
        return attemptService.start(assessmentId);
    }

    @Operation(summary = "The learner's finished attempts at this assessment")
    @GetMapping("/{assessmentId}/attempts/mine")
    public List<AssessmentDtos.AttemptResult> myAttempts(@PathVariable UUID assessmentId) {
        return attemptService.myAttempts(assessmentId);
    }

    @Operation(summary = "Reload an attempt that is in progress")
    @GetMapping("/attempts/{attemptId}")
    public AssessmentDtos.AttemptView resume(@PathVariable UUID attemptId) {
        return attemptService.resume(attemptId);
    }

    @Operation(summary = "Autosave a single answer")
    @PostMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswer(@PathVariable UUID attemptId,
                                           @Valid @RequestBody AssessmentDtos.AnswerRequest request) {
        attemptService.saveAnswer(attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Submit an attempt for grading")
    @PostMapping("/attempts/{attemptId}/submit")
    public AssessmentDtos.AttemptResult submit(@PathVariable UUID attemptId,
                                               @RequestBody(required = false) AssessmentDtos.SubmitRequest request) {
        return attemptService.submit(attemptId, request);
    }

    @Operation(summary = "The graded result for an attempt")
    @GetMapping("/attempts/{attemptId}/result")
    public AssessmentDtos.AttemptResult result(@PathVariable UUID attemptId) {
        return attemptService.result(attemptId);
    }

    // ---------------- Grading ----------------

    @Operation(summary = "Attempts waiting on a human grader")
    @GetMapping("/grading-queue")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','INSTRUCTOR')")
    public PageResponse<AssessmentDtos.GradingQueueItem> gradingQueue(
            @RequestParam(required = false) UUID assessmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return attemptService.gradingQueue(assessmentId, page, size);
    }

    @Operation(summary = "Award points and feedback for the manually graded questions in an attempt")
    @PostMapping("/attempts/{attemptId}/grade")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','INSTRUCTOR')")
    public AssessmentDtos.AttemptResult grade(@PathVariable UUID attemptId,
                                              @Valid @RequestBody AssessmentDtos.GradeSubmission submission) {
        return attemptService.gradeManually(attemptId, submission);
    }
}
