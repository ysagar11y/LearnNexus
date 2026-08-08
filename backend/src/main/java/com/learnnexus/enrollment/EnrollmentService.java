package com.learnnexus.enrollment;

import com.learnnexus.audit.AuditService;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.catalog.Course;
import com.learnnexus.catalog.Lesson;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.iam.OrgUnit;
import com.learnnexus.iam.OrgUnitRepository;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.notification.MailService;
import com.learnnexus.notification.NotificationService;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepositories.EnrollmentRepository enrollmentRepository;
    private final EnrollmentRepositories.LessonProgressRepository progressRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final CatalogRepositories.CourseModuleRepository moduleRepository;
    private final CatalogRepositories.LessonRepository lessonRepository;
    private final CatalogRepositories.CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final CertificateIssuer certificateIssuer;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;

    // =================================================================
    // Assignment
    // =================================================================

    @Transactional
    public EnrollmentDtos.BulkResult enroll(EnrollmentDtos.EnrollRequest request) {
        Course course = requireCourse(request.courseId());
        if (course.getStatus() != Course.Status.PUBLISHED) {
            throw ApiException.badRequest("course_not_published",
                    "Publish the course before assigning it to learners.");
        }

        List<User> users = userRepository.findAllActiveByIds(request.userIds());
        return enrollAll(course, users, request.dueAt(),
                request.source() == null ? Enrollment.Source.MANUAL : request.source(), request.notifyLearners());
    }

    @Transactional
    public EnrollmentDtos.BulkResult enrollOrgUnit(EnrollmentDtos.EnrollByOrgUnitRequest request) {
        Course course = requireCourse(request.courseId());
        OrgUnit unit = orgUnitRepository.findById(request.orgUnitId())
                .orElseThrow(() -> ApiException.notFound("Organisation unit", request.orgUnitId()));

        Set<UUID> unitIds = new HashSet<>();
        unitIds.add(unit.getId());
        if (request.includeSubtree()) {
            orgUnitRepository.findSubtree(unit.getId(), unit.subtreePath())
                    .forEach(descendant -> unitIds.add(descendant.getId()));
        }

        List<User> users = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> user.getOrgUnitId() != null && unitIds.contains(user.getOrgUnitId()))
                .toList();

        return enrollAll(course, users, request.dueAt(), Enrollment.Source.RULE, request.notifyLearners());
    }

    @Transactional
    public EnrollmentDtos.EnrollmentSummary selfEnroll(UUID courseId) {
        Course course = requireCourse(courseId);
        if (!course.allowsSelfEnrollment()) {
            throw ApiException.forbidden("This course is assigned by an administrator.");
        }

        User learner = userRepository.findActiveById(CurrentUser.requireId())
                .orElseThrow(() -> ApiException.unauthorized("account_missing", "This account no longer exists."));

        assertPrerequisitesMet(course, learner.getId());
        assertSeatsAvailable(course);

        Enrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, learner.getId())
                .orElseGet(() -> createEnrollment(course, learner, null, Enrollment.Source.SELF));

        if (enrollment.getStatus() == Enrollment.Status.WITHDRAWN) {
            enrollment.setStatus(Enrollment.Status.ACTIVE);
            enrollmentRepository.save(enrollment);
        }

        auditService.record(AuditService.ENROLLMENT_CREATED, "Enrollment", enrollment.getId(),
                "Self-enrolled in " + course.getTitle());
        return toSummary(enrollment, contextFor(List.of(enrollment)));
    }

    private EnrollmentDtos.BulkResult enrollAll(Course course, List<User> users, Instant dueAt,
                                                Enrollment.Source source, boolean notify) {
        TenantContext.Snapshot tenant = TenantContext.require();
        int enrolled = 0;
        int already = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();

        long seatsTaken = enrollmentRepository.countActiveForCourse(course.getId());

        for (User user : users) {
            if (enrollmentRepository.findByCourseIdAndUserId(course.getId(), user.getId()).isPresent()) {
                already++;
                continue;
            }
            if (course.getSeatLimit() != null && seatsTaken >= course.getSeatLimit()) {
                skipped++;
                messages.add(user.getEmail() + ": the course is full");
                continue;
            }

            createEnrollment(course, user, dueAt, source);
            seatsTaken++;
            enrolled++;

            notificationService.courseAssigned(user, course, dueAt);
            if (notify) {
                mailService.sendCourseAssigned(user, tenant, course.getTitle(), course.getId().toString(),
                        dueAt == null ? null : "It is due by " + formatDate(dueAt) + ".");
            }
        }

        auditService.record(AuditService.ENROLLMENT_CREATED, "Course", course.getId(),
                "Assigned " + course.getTitle() + " to " + enrolled + " learners",
                Map.of("enrolled", enrolled, "alreadyEnrolled", already, "skipped", skipped));

        return new EnrollmentDtos.BulkResult(enrolled, already, skipped, messages);
    }

    private Enrollment createEnrollment(Course course, User user, Instant dueAt, Enrollment.Source source) {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(course.getId());
        enrollment.setUserId(user.getId());
        enrollment.setSource(source);
        enrollment.setDueAt(dueAt);
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        CurrentUser.find().map(AppUserPrincipal::userId).ifPresent(enrollment::setAssignedBy);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void withdraw(UUID enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if (enrollment.getStatus() == Enrollment.Status.COMPLETED) {
            throw ApiException.conflict("already_completed",
                    "This course is already complete; the record is kept for compliance.");
        }
        enrollment.setStatus(Enrollment.Status.WITHDRAWN);
        enrollmentRepository.save(enrollment);
        auditService.record(AuditService.ENROLLMENT_WITHDRAWN, "Enrollment", enrollmentId, "Enrolment withdrawn");
    }

    @Transactional
    public EnrollmentDtos.EnrollmentSummary setDueDate(UUID enrollmentId, Instant dueAt) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        enrollment.setDueAt(dueAt);
        enrollmentRepository.save(enrollment);
        return toSummary(enrollment, contextFor(List.of(enrollment)));
    }

    // =================================================================
    // Reads
    // =================================================================

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDtos.EnrollmentSummary> search(UUID courseId, UUID userId,
                                                                 Enrollment.Status status,
                                                                 int page, int size) {
        Page<Enrollment> results = enrollmentRepository.search(courseId, userId, status,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "enrolledAt")));
        Context context = contextFor(results.getContent());
        return PageResponse.of(results, enrollment -> toSummary(enrollment, context));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDtos.EnrollmentSummary> myLearning(Enrollment.Status status) {
        List<Enrollment> enrollments = enrollmentRepository.findForLearnerByStatus(CurrentUser.requireId(), status);
        Context context = contextFor(enrollments);
        return enrollments.stream().map(enrollment -> toSummary(enrollment, context)).toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentDtos.LearnerDashboard dashboard() {
        UUID userId = CurrentUser.requireId();
        List<Enrollment> enrollments = enrollmentRepository.findForLearner(userId);
        Context context = contextFor(enrollments);

        int assigned = enrollments.size();
        int completed = (int) enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.COMPLETED).count();
        int inProgress = (int) enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE && e.getProgressPercent() > 0).count();
        int overdue = (int) enrollments.stream().filter(Enrollment::isOverdue).count();

        long certificates = jdbc.queryForLong(
                "select count(*) from certificates where tenant_id = ? and user_id = ? and revoked_at is null", userId);
        long learningMinutes = progressRepository.totalSecondsWatched(userId) / 60;

        List<EnrollmentDtos.EnrollmentSummary> continueLearning = enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
                .sorted(Comparator.comparing(
                        (Enrollment e) -> e.getLastAccessedAt() == null ? Instant.EPOCH : e.getLastAccessedAt())
                        .reversed())
                .limit(4)
                .map(e -> toSummary(e, context))
                .toList();

        List<EnrollmentDtos.EnrollmentSummary> dueSoon = enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE && e.getDueAt() != null)
                .sorted(Comparator.comparing(Enrollment::getDueAt))
                .limit(5)
                .map(e -> toSummary(e, context))
                .toList();

        return new EnrollmentDtos.LearnerDashboard(
                assigned, inProgress, completed, overdue, (int) certificates, learningMinutes,
                currentStreak(userId), continueLearning, dueSoon, upcomingSessions(userId));
    }

    /**
     * Consecutive days ending today (or yesterday) on which the learner touched a
     * lesson. Counted in the tenant's stored timestamps rather than by a rolling
     * 24-hour window, so a streak matches what a learner sees on a calendar.
     */
    private int currentStreak(UUID userId) {
        List<LocalDate> days = jdbc.query("""
                        select distinct date_trunc('day', updated_at) as day
                        from lesson_progress
                        where tenant_id = ? and user_id = ?
                        order by day desc
                        limit 400
                        """,
                (rs, rowNum) -> rs.getTimestamp("day").toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                userId);

        if (days.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cursor = days.getFirst();
        if (ChronoUnit.DAYS.between(cursor, today) > 1) {
            return 0;
        }

        int streak = 1;
        for (int index = 1; index < days.size(); index++) {
            if (ChronoUnit.DAYS.between(days.get(index), cursor) == 1) {
                streak++;
                cursor = days.get(index);
            } else {
                break;
            }
        }
        return streak;
    }

    private List<EnrollmentDtos.UpcomingSession> upcomingSessions(UUID userId) {
        return jdbc.query("""
                select s.id, s.course_id, c.title as course_title, s.title, s.provider,
                       s.join_url, s.starts_at, s.ends_at
                from live_sessions s
                join courses c on c.id = s.course_id and c.tenant_id = s.tenant_id
                join enrollments e on e.course_id = s.course_id and e.tenant_id = s.tenant_id
                where s.tenant_id = ? and e.user_id = ? and e.status = 'ACTIVE' and s.ends_at > now()
                order by s.starts_at
                limit 5
                """, (rs, rowNum) -> new EnrollmentDtos.UpcomingSession(
                rs.getObject("id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_title"),
                rs.getString("title"),
                rs.getString("provider"),
                rs.getString("join_url"),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant()), userId);
    }

    // =================================================================
    // The player
    // =================================================================

    @Transactional
    public EnrollmentDtos.PlayerView player(UUID courseId) {
        UUID userId = CurrentUser.requireId();
        Course course = requireCourse(courseId);

        Enrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not enrolled in this course."));
        if (enrollment.getStatus() == Enrollment.Status.WITHDRAWN) {
            throw ApiException.forbidden("Your enrolment in this course was withdrawn.");
        }

        enrollment.setLastAccessedAt(Instant.now());
        enrollmentRepository.save(enrollment);

        Map<UUID, LessonProgress> progressByLesson = progressRepository.findByEnrollment(enrollment.getId()).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, progress -> progress, (a, b) -> a));

        Map<UUID, AssessmentState> assessments = assessmentStates(courseId, userId);

        Map<UUID, List<Lesson>> lessonsByModule = lessonRepository.findByCourse(courseId).stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId, LinkedHashMap::new, Collectors.toList()));

        List<EnrollmentDtos.ModuleView> modules = new ArrayList<>();
        UUID nextLessonId = null;

        for (var module : moduleRepository.findByCourse(courseId)) {
            List<EnrollmentDtos.LessonView> lessonViews = new ArrayList<>();
            for (Lesson lesson : lessonsByModule.getOrDefault(module.getId(), List.of())) {
                LessonProgress progress = progressByLesson.get(lesson.getId());
                AssessmentState assessment = assessments.get(lesson.getId());

                LessonProgress.Status status = progress == null
                        ? LessonProgress.Status.NOT_STARTED : progress.getStatus();
                if (nextLessonId == null && status != LessonProgress.Status.COMPLETED) {
                    nextLessonId = lesson.getId();
                }

                lessonViews.add(new EnrollmentDtos.LessonView(
                        lesson.getId(), lesson.getTitle(), lesson.getContentType(), lesson.getContentUrl(),
                        lesson.getContentHtml(), lesson.getAssetId(), lesson.getDurationSeconds(),
                        lesson.isMandatory(), lesson.isPreview(), status,
                        progress == null ? 0 : progress.getLastPositionSeconds(),
                        progress == null ? 0 : progress.getSecondsWatched(),
                        assessment == null ? null : assessment.assessmentId(),
                        assessment != null && assessment.passed(),
                        assessment == null ? null : assessment.bestScore(),
                        false));
            }
            modules.add(new EnrollmentDtos.ModuleView(
                    module.getId(), module.getTitle(), module.getSummary(), module.getSortOrder(), lessonViews));
        }

        return new EnrollmentDtos.PlayerView(
                enrollment.getId(), course.getId(), course.getTitle(), course.getSummary(), course.getDescription(),
                course.getDeliveryType(), enrollment.getProgressPercent(), enrollment.getStatus(),
                enrollment.getDueAt(), enrollment.isOverdue(), course.isCertificateEnabled(),
                certificateIssuer.findForEnrollment(enrollment.getId()).orElse(null),
                modules, nextLessonId);
    }

    private record AssessmentState(UUID assessmentId, boolean passed, Integer bestScore) {}

    private Map<UUID, AssessmentState> assessmentStates(UUID courseId, UUID userId) {
        Map<UUID, AssessmentState> states = new HashMap<>();
        // The tenant filter is lifted into a CTE so it stays the first bind
        // parameter, which is the contract TenantAwareJdbc enforces.
        jdbc.queryForMaps("""
                        with scoped as (
                            select id, lesson_id, tenant_id
                            from assessments
                            where tenant_id = ? and course_id = ? and lesson_id is not null
                        )
                        select s.id, s.lesson_id,
                               bool_or(a.passed)  as passed,
                               max(a.percentage)  as best
                        from scoped s
                        left join attempts a
                               on a.assessment_id = s.id and a.tenant_id = s.tenant_id
                              and a.user_id = ? and a.status in ('SUBMITTED','GRADED')
                        group by s.id, s.lesson_id
                        """, courseId, userId)
                .forEach(row -> states.put((UUID) row.get("lesson_id"), new AssessmentState(
                        (UUID) row.get("id"),
                        Boolean.TRUE.equals(row.get("passed")),
                        row.get("best") == null ? null : ((Number) row.get("best")).intValue())));
        return states;
    }

    // =================================================================
    // Progress
    // =================================================================

    @Transactional
    public EnrollmentDtos.ProgressResponse recordProgress(UUID courseId,
                                                          EnrollmentDtos.ProgressUpdateRequest request) {
        UUID userId = CurrentUser.requireId();
        Enrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not enrolled in this course."));
        if (enrollment.getStatus() == Enrollment.Status.WITHDRAWN) {
            throw ApiException.forbidden("Your enrolment in this course was withdrawn.");
        }

        Lesson lesson = lessonRepository.findById(request.lessonId())
                .filter(candidate -> candidate.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.notFound("Lesson", request.lessonId()));

        LessonProgress progress = progressRepository
                .findByEnrollmentIdAndLessonId(enrollment.getId(), lesson.getId())
                .orElseGet(() -> {
                    LessonProgress fresh = new LessonProgress();
                    fresh.setEnrollmentId(enrollment.getId());
                    fresh.setLessonId(lesson.getId());
                    fresh.setUserId(userId);
                    return fresh;
                });

        progress.setLastPositionSeconds(Math.max(0, request.positionSeconds()));
        // Watched time only ever moves forward: scrubbing back through a video must
        // not erase credit the learner already earned.
        progress.setSecondsWatched(Math.max(progress.getSecondsWatched(), Math.max(0, request.watchedSeconds())));
        progress.setUpdatedAt(Instant.now());

        boolean shouldComplete = Boolean.TRUE.equals(request.completed());
        if (!shouldComplete && lesson.getContentType().isTimeBased() && lesson.getDurationSeconds() > 0) {
            shouldComplete = progress.getSecondsWatched()
                    >= lesson.getDurationSeconds() * LessonProgress.COMPLETION_THRESHOLD;
        }
        if (shouldComplete) {
            assertAssessmentPassed(lesson, userId);
            progress.complete();
        } else if (progress.getStatus() == LessonProgress.Status.NOT_STARTED) {
            progress.setStatus(LessonProgress.Status.IN_PROGRESS);
        }
        progressRepository.save(progress);

        enrollment.setLastAccessedAt(Instant.now());
        UUID certificateId = recalculateProgress(enrollment);

        return new EnrollmentDtos.ProgressResponse(
                lesson.getId(), progress.getStatus(), enrollment.getProgressPercent(), enrollment.getStatus(),
                enrollment.getStatus() == Enrollment.Status.COMPLETED, certificateId,
                nextIncompleteLesson(enrollment.getId(), courseId));
    }

    /**
     * A quiz lesson only counts as complete once its assessment has been passed,
     * otherwise a learner could skip past it and still finish the course.
     */
    private void assertAssessmentPassed(Lesson lesson, UUID userId) {
        if (lesson.getContentType() != Lesson.ContentType.QUIZ) {
            return;
        }
        long passed = jdbc.queryForLong("""
                select count(*) from attempts a
                join assessments s on s.id = a.assessment_id and s.tenant_id = a.tenant_id
                where a.tenant_id = ? and s.lesson_id = ? and a.user_id = ? and a.passed = true
                """, lesson.getId(), userId);
        if (passed == 0) {
            throw ApiException.badRequest("assessment_not_passed",
                    "Pass the quiz in this lesson before marking it complete.");
        }
    }

    /**
     * Recomputes course progress from lesson completions and, when everything
     * mandatory is done, completes the enrolment and issues a certificate.
     *
     * @return the certificate id if one was issued or already exists
     */
    private UUID recalculateProgress(Enrollment enrollment) {
        long mandatoryTotal = lessonRepository.countMandatoryByCourse(enrollment.getCourseId());
        long total = mandatoryTotal > 0 ? mandatoryTotal : lessonRepository.countByCourse(enrollment.getCourseId());

        Set<UUID> mandatoryLessonIds = lessonRepository.findByCourse(enrollment.getCourseId()).stream()
                .filter(lesson -> mandatoryTotal == 0 || lesson.isMandatory())
                .map(Lesson::getId)
                .collect(Collectors.toSet());

        long completed = progressRepository.findByEnrollment(enrollment.getId()).stream()
                .filter(progress -> progress.getStatus() == LessonProgress.Status.COMPLETED)
                .filter(progress -> mandatoryLessonIds.contains(progress.getLessonId()))
                .count();

        int percent = total == 0 ? 0 : (int) Math.min(100, Math.round(completed * 100.0 / total));
        enrollment.setProgressPercent((short) percent);

        UUID certificateId = null;
        if (percent >= 100 && enrollment.getStatus() == Enrollment.Status.ACTIVE) {
            enrollment.markCompleted();
            certificateId = certificateIssuer.issueFor(enrollment.getId()).orElse(null);
            userRepository.findActiveById(enrollment.getUserId()).ifPresent(learner ->
                    courseRepository.findActiveById(enrollment.getCourseId()).ifPresent(course ->
                            notificationService.courseCompleted(learner, course)));
        }

        enrollmentRepository.save(enrollment);
        return certificateId;
    }

    private UUID nextIncompleteLesson(UUID enrollmentId, UUID courseId) {
        Set<UUID> done = progressRepository.findByEnrollment(enrollmentId).stream()
                .filter(progress -> progress.getStatus() == LessonProgress.Status.COMPLETED)
                .map(LessonProgress::getLessonId)
                .collect(Collectors.toSet());

        return lessonRepository.findByCourse(courseId).stream()
                .filter(lesson -> !done.contains(lesson.getId()))
                .map(Lesson::getId)
                .findFirst()
                .orElse(null);
    }

    /** Called by the assessment module when an attempt is graded. */
    @Transactional
    public void onAssessmentPassed(UUID courseId, UUID userId, UUID lessonId) {
        enrollmentRepository.findByCourseIdAndUserId(courseId, userId).ifPresent(enrollment -> {
            if (lessonId != null) {
                LessonProgress progress = progressRepository
                        .findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                        .orElseGet(() -> {
                            LessonProgress fresh = new LessonProgress();
                            fresh.setEnrollmentId(enrollment.getId());
                            fresh.setLessonId(lessonId);
                            fresh.setUserId(userId);
                            return fresh;
                        });
                progress.complete();
                progressRepository.save(progress);
            }
            recalculateProgress(enrollment);
        });
    }

    // =================================================================
    // Guards and mapping
    // =================================================================

    private void assertPrerequisitesMet(Course course, UUID userId) {
        if (course.getPrerequisiteIds().length == 0) {
            return;
        }
        Set<UUID> completed = new HashSet<>(enrollmentRepository.findCompletedCourseIds(userId));
        List<UUID> missing = List.of(course.getPrerequisiteIds()).stream()
                .filter(id -> !completed.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            List<String> titles = courseRepository.findAllActiveByIds(missing).stream()
                    .map(Course::getTitle).toList();
            throw ApiException.conflict("prerequisites_unmet",
                    "Finish these first: " + String.join(", ", titles));
        }
    }

    private void assertSeatsAvailable(Course course) {
        if (course.getSeatLimit() == null) {
            return;
        }
        if (enrollmentRepository.countActiveForCourse(course.getId()) >= course.getSeatLimit()) {
            throw ApiException.conflict("course_full", "This course has no places left.");
        }
    }

    private Course requireCourse(UUID courseId) {
        return courseRepository.findActiveById(courseId)
                .orElseThrow(() -> ApiException.notFound("Course", courseId));
    }

    private Enrollment requireEnrollment(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("Enrolment", enrollmentId));

        AppUserPrincipal principal = CurrentUser.require();
        boolean privileged = principal.hasAnyRole(
                RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN, RoleCode.INSTRUCTOR, RoleCode.MANAGER);
        if (!privileged && !enrollment.getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("You cannot change someone else's enrolment.");
        }
        return enrollment;
    }

    private record Context(
            Map<UUID, Course> courses,
            Map<UUID, String> categoryNames,
            Map<UUID, User> learners,
            Map<UUID, Integer> lessonCounts,
            Map<UUID, Long> completedLessons
    ) {}

    private Context contextFor(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<UUID> courseIds = enrollments.stream().map(Enrollment::getCourseId).distinct().toList();
        List<UUID> userIds = enrollments.stream().map(Enrollment::getUserId).distinct().toList();

        Map<UUID, Course> courses = courseRepository.findAllActiveByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, course -> course));
        Map<UUID, String> categoryNames = categoryRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(category -> category.getId(), category -> category.getName()));
        Map<UUID, User> learners = userRepository.findAllActiveByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Map<UUID, Integer> lessonCounts = new HashMap<>();
        String courseIn = courseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.queryForMaps("select course_id, count(*) as total from lessons where tenant_id = ? and course_id in ("
                        + courseIn + ") group by course_id", courseIds.toArray())
                .forEach(row -> lessonCounts.put((UUID) row.get("course_id"), ((Number) row.get("total")).intValue()));

        Map<UUID, Long> completedLessons = new HashMap<>();
        List<UUID> enrollmentIds = enrollments.stream().map(Enrollment::getId).toList();
        String enrollmentIn = enrollmentIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.queryForMaps("""
                        select enrollment_id, count(*) as total from lesson_progress
                        where tenant_id = ? and status = 'COMPLETED' and enrollment_id in (""" + enrollmentIn
                        + ") group by enrollment_id", enrollmentIds.toArray())
                .forEach(row -> completedLessons.put((UUID) row.get("enrollment_id"),
                        ((Number) row.get("total")).longValue()));

        return new Context(courses, categoryNames, learners, lessonCounts, completedLessons);
    }

    private EnrollmentDtos.EnrollmentSummary toSummary(Enrollment enrollment, Context context) {
        Course course = context.courses().get(enrollment.getCourseId());
        User learner = context.learners().get(enrollment.getUserId());

        return new EnrollmentDtos.EnrollmentSummary(
                enrollment.getId(),
                enrollment.getCourseId(),
                course == null ? "Removed course" : course.getTitle(),
                course == null ? null : course.getSlug(),
                course == null ? null : course.getThumbnailUrl(),
                course == null || course.getCategoryId() == null
                        ? null : context.categoryNames().get(course.getCategoryId()),
                course == null ? Course.DeliveryType.SELF_PACED : course.getDeliveryType(),
                enrollment.getUserId(),
                learner == null ? null : learner.displayName(),
                learner == null ? null : learner.getEmail(),
                enrollment.getStatus(),
                enrollment.getSource(),
                enrollment.getProgressPercent(),
                context.completedLessons().getOrDefault(enrollment.getId(), 0L).intValue(),
                context.lessonCounts().getOrDefault(enrollment.getCourseId(), 0),
                course == null ? 0 : course.getEstimatedMinutes(),
                course != null && course.isMandatory(),
                enrollment.isOverdue(),
                enrollment.getDueAt(),
                enrollment.getCompletedAt(),
                enrollment.getLastAccessedAt(),
                enrollment.getEnrolledAt());
    }

    private static String formatDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Sweeps enrolments whose access window has closed. */
    @Transactional
    public int expireLapsedEnrollments() {
        return jdbc.update("""
                update enrollments set status = 'EXPIRED'
                where tenant_id = ? and status = 'ACTIVE'
                  and expires_at is not null and expires_at < now()
                """);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> dueWithin(Duration window) {
        Instant now = Instant.now();
        return enrollmentRepository.findDueBetween(now, now.plus(window));
    }

    Optional<Enrollment> findById(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId);
    }
}
