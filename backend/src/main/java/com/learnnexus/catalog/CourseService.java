package com.learnnexus.catalog;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.common.Slugs;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CatalogRepositories.CourseRepository courseRepository;
    private final CatalogRepositories.CourseModuleRepository moduleRepository;
    private final CatalogRepositories.LessonRepository lessonRepository;
    private final CatalogRepositories.CourseInstructorRepository instructorRepository;
    private final CatalogRepositories.CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<CatalogDtos.CourseSummary> search(String query, Course.Status status, UUID categoryId,
                                                          Course.Level level, Course.DeliveryType deliveryType,
                                                          String tag, int page, int size, String sort) {
        Page<Course> results = courseRepository.search(
                blankToNull(query), status, categoryId, level, deliveryType,
                PageRequest.of(page, Math.min(size, 100), sortOf(sort)));

        List<Course> courses = results.getContent();
        // Tag filtering is applied after the query because the array containment
        // operator is awkward to express portably in JPQL and the page is small.
        List<Course> filtered = tag == null || tag.isBlank()
                ? courses
                : courses.stream().filter(course -> Arrays.stream(course.getTags())
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(tag))).toList();

        Context context = contextFor(filtered);
        List<CatalogDtos.CourseSummary> items = filtered.stream()
                .map(course -> toSummary(course, context))
                .toList();

        return new PageResponse<>(items, results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(), results.hasNext());
    }

    @Transactional(readOnly = true)
    public CatalogDtos.CourseDetail get(UUID courseId) {
        Course course = requireCourse(courseId);
        Context context = contextFor(List.of(course));

        List<CourseModule> modules = moduleRepository.findByCourse(courseId);
        Map<UUID, List<Lesson>> lessonsByModule = lessonRepository.findByCourse(courseId).stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, UUID> assessmentByLesson = assessmentIdsByLesson(courseId);

        List<CatalogDtos.ModuleDetail> moduleDetails = modules.stream()
                .map(module -> new CatalogDtos.ModuleDetail(
                        module.getId(), module.getTitle(), module.getSummary(), module.getSortOrder(),
                        lessonsByModule.getOrDefault(module.getId(), List.of()).stream()
                                .map(lesson -> toLessonDetail(lesson, assessmentByLesson.get(lesson.getId())))
                                .toList()))
                .toList();

        List<User> instructors = userRepository.findAllActiveByIds(
                instructorRepository.findByCourse(courseId).stream().map(CourseInstructor::getUserId).toList());

        List<Course> prerequisites = course.getPrerequisiteIds().length == 0
                ? List.of()
                : courseRepository.findAllActiveByIds(Arrays.asList(course.getPrerequisiteIds()));

        return new CatalogDtos.CourseDetail(
                toSummary(course, context),
                course.getDescription(),
                course.getLanguage(),
                course.getVersion(),
                course.getSeatLimit(),
                course.getPassingScore(),
                course.getOwnerId(),
                course.getCertificateTemplateId(),
                Arrays.asList(course.getPrerequisiteIds()),
                prerequisites.stream()
                        .map(pre -> new CatalogDtos.PrerequisiteRef(pre.getId(), pre.getTitle(), pre.getStatus()))
                        .toList(),
                instructors.stream()
                        .map(user -> new CatalogDtos.InstructorRef(
                                user.getId(), user.displayName(), user.getEmail(), user.getAvatarUrl()))
                        .toList(),
                moduleDetails,
                statsFor(courseId));
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CourseSummary> coursesForInstructor(UUID instructorId) {
        List<UUID> assigned = new ArrayList<>(instructorRepository.findCourseIdsForInstructor(instructorId));
        courseRepository.findByOwner(instructorId).forEach(course -> {
            if (!assigned.contains(course.getId())) {
                assigned.add(course.getId());
            }
        });
        if (assigned.isEmpty()) {
            return List.of();
        }
        List<Course> courses = courseRepository.findAllActiveByIds(assigned);
        Context context = contextFor(courses);
        return courses.stream().map(course -> toSummary(course, context)).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDtos.CourseStats statsFor(UUID courseId) {
        String sql = """
                select
                  count(e.id)                                                        as enrolled,
                  count(e.id) filter (where e.status = 'COMPLETED')                   as completed,
                  count(e.id) filter (where e.status = 'ACTIVE')                      as in_progress,
                  count(e.id) filter (where e.status = 'ACTIVE' and e.due_at < now()) as overdue,
                  coalesce(round(avg(e.progress_percent)), 0)                         as avg_progress,
                  (select count(*) from certificates c
                     where c.tenant_id = e2.tenant_id and c.course_id = e2.course_id
                       and c.revoked_at is null)                                      as certificates,
                  (select round(avg(a.percentage)) from attempts a
                     join assessments s on s.id = a.assessment_id and s.tenant_id = a.tenant_id
                    where a.tenant_id = e2.tenant_id and s.course_id = e2.course_id
                      and a.status in ('SUBMITTED','GRADED'))                         as avg_score
                from (select ?::uuid as tenant_id, ?::uuid as course_id) e2
                left join enrollments e
                       on e.tenant_id = e2.tenant_id and e.course_id = e2.course_id
                group by e2.tenant_id, e2.course_id
                """;

        return jdbc.queryOne(sql, (rs, rowNum) -> {
            int avgScore = rs.getInt("avg_score");
            return new CatalogDtos.CourseStats(
                    rs.getLong("enrolled"), rs.getLong("completed"), rs.getLong("in_progress"),
                    rs.getLong("overdue"), rs.getInt("avg_progress"), rs.getLong("certificates"),
                    rs.wasNull() ? null : avgScore);
        }, courseId).orElse(new CatalogDtos.CourseStats(0, 0, 0, 0, 0, 0, null));
    }

    // -----------------------------------------------------------------
    // Course writes
    // -----------------------------------------------------------------

    @Transactional
    public CatalogDtos.CourseDetail create(CatalogDtos.CourseRequest request) {
        Course course = new Course();
        course.setSlug(Slugs.unique(request.title(), courseRepository::existsBySlugIgnoreCase));
        course.setCreatedBy(CurrentUser.requireId());
        course.setOwnerId(request.ownerId() == null ? CurrentUser.requireId() : request.ownerId());
        apply(course, request);
        courseRepository.save(course);

        replaceInstructors(course.getId(), request.instructorIds());

        // A course with no sections cannot be edited meaningfully, so give it one.
        CourseModule first = new CourseModule();
        first.setCourseId(course.getId());
        first.setTitle("Getting started");
        first.setSortOrder((short) 0);
        moduleRepository.save(first);

        auditService.record(AuditService.COURSE_CREATED, "Course", course.getId(),
                "Created course " + course.getTitle());
        return get(course.getId());
    }

    @Transactional
    public CatalogDtos.CourseDetail update(UUID courseId, CatalogDtos.CourseRequest request) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        if (!course.getTitle().equals(request.title())) {
            course.setSlug(Slugs.unique(request.title(), candidate ->
                    !candidate.equals(course.getSlug()) && courseRepository.existsBySlugIgnoreCase(candidate)));
        }
        if (request.ownerId() != null) {
            course.setOwnerId(request.ownerId());
        }
        apply(course, request);
        courseRepository.save(course);
        replaceInstructors(courseId, request.instructorIds());

        auditService.record(AuditService.COURSE_UPDATED, "Course", courseId,
                "Updated course " + course.getTitle());
        return get(courseId);
    }

    private void apply(Course course, CatalogDtos.CourseRequest request) {
        validateCategory(request.categoryId());

        course.setTitle(request.title().trim());
        course.setCode(blankToNull(request.code()));
        course.setSummary(blankToNull(request.summary()));
        course.setDescription(blankToNull(request.description()));
        course.setThumbnailUrl(blankToNull(request.thumbnailUrl()));
        course.setCategoryId(request.categoryId());
        course.setLevel(request.level() == null ? Course.Level.BEGINNER : request.level());
        course.setDeliveryType(request.deliveryType() == null
                ? Course.DeliveryType.SELF_PACED : request.deliveryType());
        course.setEnrollmentMode(request.enrollmentMode() == null
                ? Course.EnrollmentMode.MANUAL : request.enrollmentMode());
        course.setLanguage(request.language() == null || request.language().isBlank() ? "en" : request.language());
        course.setEstimatedMinutes(request.estimatedMinutes());
        course.setSeatLimit(request.seatLimit());
        course.setPassingScore((short) request.passingScore());
        course.setMandatory(request.mandatory());
        course.setCertificateEnabled(request.certificateEnabled());
        course.setCertificateTemplateId(request.certificateTemplateId());
        course.setTags(normaliseTags(request.tags()));
        course.setPrerequisiteIds(validatePrerequisites(course.getId(), request.prerequisiteIds()));
        course.setUpdatedAt(Instant.now());
    }

    @Transactional
    public CatalogDtos.CourseDetail changeStatus(UUID courseId, Course.Status status) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        switch (status) {
            case PUBLISHED -> {
                course.publish(lessonRepository.countByCourse(courseId));
                auditService.record(AuditService.COURSE_PUBLISHED, "Course", courseId,
                        "Published " + course.getTitle());
            }
            case ARCHIVED -> {
                course.setStatus(Course.Status.ARCHIVED);
                auditService.record(AuditService.COURSE_ARCHIVED, "Course", courseId,
                        "Archived " + course.getTitle());
            }
            default -> {
                course.setStatus(status);
                auditService.record(AuditService.COURSE_UPDATED, "Course", courseId,
                        "Status set to " + status);
            }
        }
        course.setUpdatedAt(Instant.now());
        courseRepository.save(course);
        return get(courseId);
    }

    @Transactional
    public void delete(UUID courseId) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        long enrolled = jdbc.queryForLong(
                "select count(*) from enrollments where tenant_id = ? and course_id = ?", courseId);
        if (enrolled > 0) {
            throw ApiException.conflict("course_in_use",
                    "This course has " + enrolled + " enrolments. Archive it instead so learner records survive.");
        }

        course.setDeletedAt(Instant.now());
        course.setStatus(Course.Status.ARCHIVED);
        courseRepository.save(course);
        auditService.record(AuditService.COURSE_ARCHIVED, "Course", courseId,
                "Deleted course " + course.getTitle());
    }

    // -----------------------------------------------------------------
    // Modules
    // -----------------------------------------------------------------

    @Transactional
    public CatalogDtos.CourseDetail addModule(UUID courseId, CatalogDtos.ModuleRequest request) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        CourseModule module = new CourseModule();
        module.setCourseId(courseId);
        module.setTitle(request.title().trim());
        module.setSummary(blankToNull(request.summary()));
        module.setSortOrder((short) (moduleRepository.maxSortOrder(courseId) + 1));
        moduleRepository.save(module);

        touch(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail updateModule(UUID courseId, UUID moduleId, CatalogDtos.ModuleRequest request) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        CourseModule module = requireModule(courseId, moduleId);
        module.setTitle(request.title().trim());
        module.setSummary(blankToNull(request.summary()));
        module.setUpdatedAt(Instant.now());
        moduleRepository.save(module);

        touch(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail deleteModule(UUID courseId, UUID moduleId) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        CourseModule module = requireModule(courseId, moduleId);
        if (moduleRepository.findByCourse(courseId).size() == 1) {
            throw ApiException.conflict("last_module", "A course needs at least one section.");
        }
        // Lessons cascade at the database level via the module foreign key.
        moduleRepository.delete(module);

        touch(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail reorderModules(UUID courseId, List<UUID> orderedIds) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        Map<UUID, CourseModule> byId = moduleRepository.findByCourse(courseId).stream()
                .collect(Collectors.toMap(CourseModule::getId, module -> module));

        short position = 0;
        for (UUID id : orderedIds) {
            CourseModule module = byId.get(id);
            if (module != null) {
                module.setSortOrder(position++);
                moduleRepository.save(module);
            }
        }
        touch(course);
        return get(courseId);
    }

    // -----------------------------------------------------------------
    // Lessons
    // -----------------------------------------------------------------

    @Transactional
    public CatalogDtos.CourseDetail addLesson(UUID courseId, UUID moduleId, CatalogDtos.LessonRequest request) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);
        requireModule(courseId, moduleId);

        Lesson lesson = new Lesson();
        lesson.setCourseId(courseId);
        lesson.setModuleId(moduleId);
        applyLesson(lesson, request);
        lesson.setSortOrder((short) (lessonRepository.maxSortOrder(moduleId) + 1));
        lessonRepository.save(lesson);

        recalculateDuration(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail updateLesson(UUID courseId, UUID lessonId, CatalogDtos.LessonRequest request) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        Lesson lesson = requireLesson(courseId, lessonId);
        applyLesson(lesson, request);
        lesson.setUpdatedAt(Instant.now());
        lessonRepository.save(lesson);

        recalculateDuration(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail deleteLesson(UUID courseId, UUID lessonId) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        Lesson lesson = requireLesson(courseId, lessonId);
        lessonRepository.delete(lesson);

        recalculateDuration(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail moveLesson(UUID courseId, UUID lessonId, UUID targetModuleId, int position) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        Lesson lesson = requireLesson(courseId, lessonId);
        requireModule(courseId, targetModuleId);

        lesson.setModuleId(targetModuleId);
        lessonRepository.save(lesson);

        // Renumber the destination so the dragged lesson lands exactly where it was dropped.
        List<Lesson> siblings = new ArrayList<>(lessonRepository.findByModule(targetModuleId));
        siblings.removeIf(candidate -> candidate.getId().equals(lessonId));
        int index = Math.max(0, Math.min(position, siblings.size()));
        siblings.add(index, lesson);

        short order = 0;
        for (Lesson sibling : siblings) {
            sibling.setSortOrder(order++);
            lessonRepository.save(sibling);
        }

        touch(course);
        return get(courseId);
    }

    @Transactional
    public CatalogDtos.CourseDetail reorderLessons(UUID courseId, UUID moduleId, List<UUID> orderedIds) {
        Course course = requireCourse(courseId);
        assertCanEdit(course);

        Map<UUID, Lesson> byId = lessonRepository.findByModule(moduleId).stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));

        short position = 0;
        for (UUID id : orderedIds) {
            Lesson lesson = byId.get(id);
            if (lesson != null) {
                lesson.setSortOrder(position++);
                lessonRepository.save(lesson);
            }
        }
        touch(course);
        return get(courseId);
    }

    private void applyLesson(Lesson lesson, CatalogDtos.LessonRequest request) {
        lesson.setTitle(request.title().trim());
        lesson.setContentType(request.contentType() == null ? Lesson.ContentType.HTML : request.contentType());
        lesson.setContentUrl(blankToNull(request.contentUrl()));
        lesson.setContentHtml(blankToNull(request.contentHtml()));
        lesson.setAssetId(request.assetId());
        lesson.setDurationSeconds(Math.max(0, request.durationSeconds()));
        lesson.setPreview(request.preview());
        lesson.setMandatory(request.mandatory());
    }

    /** Keeps the advertised course length honest as lessons change. */
    private void recalculateDuration(Course course) {
        long seconds = lessonRepository.totalDurationSeconds(course.getId());
        if (seconds > 0) {
            course.setEstimatedMinutes((int) Math.ceil(seconds / 60.0));
        }
        touch(course);
    }

    private void touch(Course course) {
        course.setUpdatedAt(Instant.now());
        courseRepository.save(course);
    }

    // -----------------------------------------------------------------
    // Categories
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryResponse> categories() {
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.queryForMaps("""
                select category_id, count(*) as total from courses
                where tenant_id = ? and deleted_at is null and category_id is not null
                group by category_id
                """).forEach(row -> counts.put((UUID) row.get("category_id"), ((Number) row.get("total")).longValue()));

        return categoryRepository.findAllOrdered().stream()
                .map(category -> new CatalogDtos.CategoryResponse(
                        category.getId(), category.getName(), category.getSlug(), category.getDescription(),
                        category.getColor(), category.getSortOrder(), counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CatalogDtos.CategoryResponse createCategory(CatalogDtos.CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name().trim());
        category.setSlug(Slugs.unique(request.name(), categoryRepository::existsBySlugIgnoreCase));
        category.setDescription(blankToNull(request.description()));
        category.setColor(blankToNull(request.color()));
        category.setSortOrder((short) categoryRepository.findAllOrdered().size());
        categoryRepository.save(category);
        return new CatalogDtos.CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getColor(), category.getSortOrder(), 0);
    }

    @Transactional
    public CatalogDtos.CategoryResponse updateCategory(UUID categoryId, CatalogDtos.CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Category", categoryId));
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setColor(blankToNull(request.color()));
        categoryRepository.save(category);
        return categories().stream()
                .filter(candidate -> candidate.id().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Category", categoryId));
        long inUse = jdbc.queryForLong(
                "select count(*) from courses where tenant_id = ? and category_id = ? and deleted_at is null",
                categoryId);
        if (inUse > 0) {
            throw ApiException.conflict("category_in_use",
                    "Move the " + inUse + " courses in this category first.");
        }
        categoryRepository.delete(category);
    }

    // -----------------------------------------------------------------
    // Guards and helpers
    // -----------------------------------------------------------------

    /**
     * Admins may edit anything. Authors and instructors may only edit courses they
     * own or are assigned to, which is what makes the shared content library safe
     * to open up to non-admins.
     */
    public void assertCanEdit(Course course) {
        AppUserPrincipal principal = CurrentUser.require();
        if (principal.hasAnyRole(RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN)) {
            return;
        }
        boolean owns = Objects.equals(course.getOwnerId(), principal.userId());
        boolean assigned = instructorRepository.countAssignment(course.getId(), principal.userId()) > 0;
        if (!owns && !assigned) {
            throw ApiException.forbidden("You can only edit courses you own or teach.");
        }
    }

    private void replaceInstructors(UUID courseId, List<UUID> instructorIds) {
        instructorRepository.deleteByCourse(courseId);
        if (instructorIds == null || instructorIds.isEmpty()) {
            return;
        }
        Set<UUID> valid = userRepository.findAllActiveByIds(instructorIds).stream()
                .map(User::getId).collect(Collectors.toSet());
        valid.forEach(userId -> instructorRepository.save(new CourseInstructor(courseId, userId)));
    }

    private UUID[] validatePrerequisites(UUID courseId, List<UUID> prerequisiteIds) {
        if (prerequisiteIds == null || prerequisiteIds.isEmpty()) {
            return new UUID[0];
        }
        if (courseId != null && prerequisiteIds.contains(courseId)) {
            throw ApiException.badRequest("invalid_prerequisite", "A course cannot require itself.");
        }
        List<UUID> existing = courseRepository.findAllActiveByIds(prerequisiteIds).stream()
                .map(Course::getId).toList();
        return existing.toArray(new UUID[0]);
    }

    private void validateCategory(UUID categoryId) {
        if (categoryId != null && categoryRepository.findById(categoryId).isEmpty()) {
            throw ApiException.badRequest("unknown_category", "That category does not exist.");
        }
    }

    private String[] normaliseTags(List<String> tags) {
        if (tags == null) {
            return new String[0];
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty() && tag.length() <= 60)
                .distinct()
                .limit(20)
                .toArray(String[]::new);
    }

    private Course requireCourse(UUID courseId) {
        return courseRepository.findActiveById(courseId)
                .orElseThrow(() -> ApiException.notFound("Course", courseId));
    }

    private CourseModule requireModule(UUID courseId, UUID moduleId) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> ApiException.notFound("Section", moduleId));
        if (!module.getCourseId().equals(courseId)) {
            throw ApiException.notFound("Section", moduleId);
        }
        return module;
    }

    private Lesson requireLesson(UUID courseId, UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiException.notFound("Lesson", lessonId));
        if (!lesson.getCourseId().equals(courseId)) {
            throw ApiException.notFound("Lesson", lessonId);
        }
        return lesson;
    }

    /** Lookups shared by every row of a course list, fetched once instead of per row. */
    private record Context(
            Map<UUID, Category> categories,
            Map<UUID, String> ownerNames,
            Map<UUID, Integer> lessonCounts,
            Map<UUID, long[]> enrolment
    ) {}

    private Context contextFor(Collection<Course> courses) {
        Map<UUID, Category> categories = categoryRepository.findAllOrdered().stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        List<UUID> ownerIds = courses.stream().map(Course::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : userRepository.findAllActiveByIds(ownerIds).stream()
                        .collect(Collectors.toMap(User::getId, User::displayName));

        Map<UUID, Integer> lessonCounts = new HashMap<>();
        Map<UUID, long[]> enrolment = new HashMap<>();
        if (!courses.isEmpty()) {
            List<UUID> ids = courses.stream().map(Course::getId).toList();
            String inClause = ids.stream().map(id -> "?").collect(Collectors.joining(","));

            Object[] params = ids.toArray();
            jdbc.queryForMaps("select course_id, count(*) as total from lessons where tenant_id = ? and course_id in ("
                            + inClause + ") group by course_id", params)
                    .forEach(row -> lessonCounts.put((UUID) row.get("course_id"),
                            ((Number) row.get("total")).intValue()));

            jdbc.queryForMaps("""
                            select course_id, count(*) as total, coalesce(round(avg(progress_percent)), 0) as avg_progress
                            from enrollments where tenant_id = ? and course_id in (""" + inClause + ") group by course_id",
                            params)
                    .forEach(row -> enrolment.put((UUID) row.get("course_id"), new long[]{
                            ((Number) row.get("total")).longValue(),
                            ((Number) row.get("avg_progress")).longValue()}));
        }

        return new Context(categories, ownerNames, lessonCounts, enrolment);
    }

    private CatalogDtos.CourseSummary toSummary(Course course, Context context) {
        Category category = course.getCategoryId() == null ? null : context.categories().get(course.getCategoryId());
        long[] enrolment = context.enrolment().get(course.getId());

        return new CatalogDtos.CourseSummary(
                course.getId(), course.getCode(), course.getTitle(), course.getSlug(), course.getSummary(),
                course.getThumbnailUrl(), course.getLevel(), course.getDeliveryType(), course.getStatus(),
                course.getEnrollmentMode(), course.getCategoryId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getColor(),
                Arrays.asList(course.getTags()),
                course.getEstimatedMinutes(),
                context.lessonCounts().getOrDefault(course.getId(), 0),
                course.isMandatory(), course.isCertificateEnabled(),
                context.ownerNames().get(course.getOwnerId()),
                enrolment == null ? 0 : enrolment[0],
                enrolment == null ? null : (int) enrolment[1],
                course.getPublishedAt(), course.getUpdatedAt());
    }

    private CatalogDtos.LessonDetail toLessonDetail(Lesson lesson, UUID assessmentId) {
        return new CatalogDtos.LessonDetail(
                lesson.getId(), lesson.getModuleId(), lesson.getTitle(), lesson.getContentType(),
                lesson.getContentUrl(), lesson.getContentHtml(), lesson.getAssetId(),
                lesson.getDurationSeconds(), lesson.getSortOrder(), lesson.isPreview(),
                lesson.isMandatory(), assessmentId);
    }

    private Map<UUID, UUID> assessmentIdsByLesson(UUID courseId) {
        Map<UUID, UUID> result = new HashMap<>();
        jdbc.queryForMaps("select id, lesson_id from assessments where tenant_id = ? and course_id = ? and lesson_id is not null",
                        courseId)
                .forEach(row -> result.put((UUID) row.get("lesson_id"), (UUID) row.get("id")));
        return result;
    }

    private static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        String[] parts = sort.split(",");
        String property = switch (parts[0]) {
            case "title" -> "title";
            case "created" -> "createdAt";
            case "published" -> "publishedAt";
            default -> "updatedAt";
        };
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
