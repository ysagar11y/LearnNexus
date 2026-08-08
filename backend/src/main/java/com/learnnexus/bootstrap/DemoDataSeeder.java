package com.learnnexus.bootstrap;

import com.learnnexus.assessment.Assessment;
import com.learnnexus.assessment.AssessmentRepositories;
import com.learnnexus.assessment.Question;
import com.learnnexus.assessment.QuestionOption;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.catalog.Category;
import com.learnnexus.catalog.Course;
import com.learnnexus.catalog.CourseInstructor;
import com.learnnexus.catalog.CourseModule;
import com.learnnexus.catalog.Lesson;
import com.learnnexus.certificate.CertificateRenderer;
import com.learnnexus.certificate.CertificateRepositories;
import com.learnnexus.certificate.CertificateTemplate;
import com.learnnexus.common.Slugs;
import com.learnnexus.config.AppProperties;
import com.learnnexus.enrollment.CertificateIssuer;
import com.learnnexus.enrollment.Enrollment;
import com.learnnexus.enrollment.EnrollmentRepositories;
import com.learnnexus.enrollment.LessonProgress;
import com.learnnexus.iam.OrgUnit;
import com.learnnexus.iam.OrgUnitRepository;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenancy.TenantScopedExecutor;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantBranding;
import com.learnnexus.tenant.TenantBrandingRepository;
import com.learnnexus.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds a realistic demonstration environment on first boot.
 *
 * <p>Runs only when the database has no tenants, so restarting never duplicates
 * data and never overwrites anything a reviewer has changed by hand. Disable
 * entirely with {@code app.seed.enabled=false}.
 *
 * <p>Two customer tenants are created with deliberately different brand hues:
 * the fastest way to confirm that theming is genuinely derived from
 * {@code tenant_branding} rather than hardcoded is to sign into both.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final CatalogRepositories.CategoryRepository categoryRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final CatalogRepositories.CourseModuleRepository moduleRepository;
    private final CatalogRepositories.LessonRepository lessonRepository;
    private final CatalogRepositories.CourseInstructorRepository courseInstructorRepository;
    private final AssessmentRepositories.AssessmentRepository assessmentRepository;
    private final AssessmentRepositories.QuestionRepository questionRepository;
    private final AssessmentRepositories.QuestionOptionRepository optionRepository;
    private final EnrollmentRepositories.EnrollmentRepository enrollmentRepository;
    private final EnrollmentRepositories.LessonProgressRepository progressRepository;
    private final CertificateRepositories.CertificateTemplateRepository certificateTemplateRepository;
    private final CertificateIssuer certificateIssuer;
    private final TenantScopedExecutor executor;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    /** Fixed seed so demo data is identical on every fresh install. */
    private final Random random = new Random(20260807L);

    /**
     * Deliberately not {@code @Transactional}: each tenant's data is written in
     * its own transaction opened after the tenant context is set, because
     * Hibernate fixes the tenant when a session opens. See
     * {@link com.learnnexus.tenancy.TenantScopedExecutor}.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            log.info("Demo seeding disabled");
            return;
        }
        if (tenantRepository.count() > 0) {
            log.info("Tenants already present; skipping demo seed");
            return;
        }

        log.info("Seeding demo data …");
        String password = passwordEncoder.encode(properties.seed().demoPassword());

        seedSystemTenant(password);
        seedAcme(password);
        seedNorthwind(password);

        log.info("""

                ────────────────────────────────────────────────────────────
                 LearnNexus demo data ready. Password for every account:  {}

                   Platform console   platform  ·  ops@learnnexus.app
                   Acme Corp          acme      ·  priya@acme.test        (admin)
                                                ·  daniel@acme.test       (instructor)
                                                ·  sara@acme.test         (manager)
                                                ·  arjun@acme.test        (learner)
                   Northwind          northwind ·  helena@northwind.test  (admin)
                                                ·  tom@northwind.test     (learner)
                ────────────────────────────────────────────────────────────
                """, properties.seed().demoPassword());
    }

    // =================================================================
    // Tenants
    // =================================================================

    private void seedSystemTenant(String password) {
        Tenant platform = new Tenant(UUID.randomUUID(), properties.tenancy().systemSlug(), "LearnNexus Platform");
        platform.setSystemTenant(true);
        platform.setStatus(Tenant.Status.ACTIVE);
        platform.setPlan(Tenant.Plan.ENTERPRISE);
        platform.setMaxUsers(50);
        tenantRepository.save(platform);
        brandingRepository.save(new TenantBranding(platform.getId()));

        inTenant(platform, () -> {
            User ops = user("ops@learnnexus.app", "Ops", "Console", password,
                    Set.of(RoleCode.PLATFORM_ADMIN, RoleCode.TENANT_ADMIN), null, "Platform Operations");
            userRepository.save(ops);
        });
    }

    private void seedAcme(String password) {
        Tenant acme = tenant("acme", "Acme Corp", Tenant.Plan.ENTERPRISE, 500, Tenant.Status.ACTIVE);
        branding(acme, 232, "0.130", 38,
                "Build what's next.",
                "Acme's learning platform for engineering, sales and compliance training.",
                "learning@acme.test");

        inTenant(acme, () -> {
            // ---- organisation ----
            OrgUnit root = orgUnit("Acme Corp", "ACME", null);
            OrgUnit engineering = orgUnit("Engineering", "ENG", root);
            OrgUnit backend = orgUnit("Backend", "ENG-BE", engineering);
            OrgUnit mobile = orgUnit("Mobile", "ENG-MOB", engineering);
            OrgUnit sales = orgUnit("Sales", "SALES", root);
            OrgUnit people = orgUnit("People & Culture", "HR", root);

            // ---- people ----
            User priya = save(user("priya@acme.test", "Priya", "Nair", password,
                    Set.of(RoleCode.TENANT_ADMIN), people, "Head of Learning"));
            User daniel = save(user("daniel@acme.test", "Daniel", "Okonkwo", password,
                    Set.of(RoleCode.INSTRUCTOR, RoleCode.AUTHOR), engineering, "Principal Engineer"));
            User sara = save(user("sara@acme.test", "Sara", "Lindqvist", password,
                    Set.of(RoleCode.MANAGER), engineering, "Engineering Manager"));
            User arjun = save(user("arjun@acme.test", "Arjun", "Mehta", password,
                    Set.of(RoleCode.LEARNER), backend, "Senior Backend Engineer"));

            List<User> learners = new ArrayList<>(List.of(arjun));
            String[][] roster = {
                    {"lena@acme.test", "Lena", "Fischer", "Backend Engineer"},
                    {"marcus@acme.test", "Marcus", "Bell", "Mobile Engineer"},
                    {"yuki@acme.test", "Yuki", "Tanaka", "Mobile Engineer"},
                    {"omar@acme.test", "Omar", "Haddad", "Account Executive"},
                    {"chloe@acme.test", "Chloé", "Dubois", "Account Executive"},
                    {"ravi@acme.test", "Ravi", "Iyer", "Backend Engineer"},
                    {"nina@acme.test", "Nina", "Kowalski", "Sales Engineer"},
                    {"tomas@acme.test", "Tomás", "Ferreira", "Backend Engineer"},
            };
            OrgUnit[] units = {backend, mobile, mobile, sales, sales, backend, sales, backend};
            for (int index = 0; index < roster.length; index++) {
                String[] row = roster[index];
                learners.add(save(user(row[0], row[1], row[2], password,
                        Set.of(RoleCode.LEARNER), units[index], row[3])));
            }
            // Someone mid-invitation makes the admin user list look like a real one.
            User invited = user("noah@acme.test", "Noah", "Bergström", null,
                    Set.of(RoleCode.LEARNER), sales, "Sales Development");
            invited.setStatus(User.Status.INVITED);
            save(invited);

            for (User learner : learners) {
                learner.setManagerId(sara.getId());
                userRepository.save(learner);
            }

            certificateTemplate("Acme completion certificate");

            // ---- catalog ----
            Category engineeringCat = category("Engineering", "Technical craft and platform skills", "#5B6CFF", 0);
            Category complianceCat = category("Compliance", "Mandatory policy and regulatory training", "#E8833A", 1);
            Category leadershipCat = category("Leadership", "Management and communication", "#3AA88B", 2);

            Course distributed = course("Designing Distributed Systems", engineeringCat, daniel,
                    "Consistency, partitioning and failure modes for services that must not lose data.",
                    Course.Level.ADVANCED, 220, false, List.of("architecture", "backend", "scale"));
            modulesFor(distributed, new String[][]{
                    {"Foundations", "Why distribution changes every assumption you have."},
                    {"Data & consistency", "Replication, quorums and the cost of strong guarantees."},
                    {"Failure & recovery", "Designing for the day the network partitions."}
            }, new String[][][]{
                    {{"The eight fallacies, revisited", "VIDEO", "840"},
                     {"Latency budgets in practice", "VIDEO", "1020"},
                     {"Reading: partition tolerance", "HTML", "600"}},
                    {{"Replication strategies", "VIDEO", "1260"},
                     {"Quorums and read repair", "VIDEO", "1140"},
                     {"Choosing a consistency model", "HTML", "720"}},
                    {{"Retries, backoff and idempotency", "VIDEO", "960"},
                     {"Designing a runbook", "PDF", "480"},
                     {"Knowledge check", "QUIZ", "600"}}
            });
            quizFor(distributed, "Distributed systems knowledge check");
            publish(distributed);

            Course security = course("Security Essentials for Engineers", engineeringCat, daniel,
                    "The OWASP Top 10, threat modelling and the habits that keep them out of your code.",
                    Course.Level.INTERMEDIATE, 150, true, List.of("security", "owasp", "mandatory"));
            modulesFor(security, new String[][]{
                    {"Thinking like an attacker", "Threat modelling without the ceremony."},
                    {"The usual suspects", "Injection, broken access control and friends."}
            }, new String[][][]{
                    {{"What actually gets exploited", "VIDEO", "720"},
                     {"Threat modelling a feature", "VIDEO", "900"}},
                    {{"Injection and parameterisation", "VIDEO", "840"},
                     {"Broken access control", "VIDEO", "780"},
                     {"Secrets and key handling", "HTML", "540"},
                     {"Security assessment", "QUIZ", "900"}}
            });
            quizFor(security, "Security essentials assessment");
            publish(security);

            Course code = course("Code of Conduct & Workplace Policy", complianceCat, priya,
                    "The annual policy refresher every employee must complete.",
                    Course.Level.BEGINNER, 45, true, List.of("compliance", "policy", "annual"));
            modulesFor(code, new String[][]{
                    {"Our commitments", "What we expect of each other."},
                    {"Speaking up", "Reporting channels and what happens next."}
            }, new String[][][]{
                    {{"Welcome from the CEO", "VIDEO", "300"},
                     {"The policy in full", "PDF", "900"}},
                    {{"Raising a concern", "VIDEO", "420"},
                     {"Policy acknowledgement", "QUIZ", "300"}}
            });
            quizFor(code, "Policy acknowledgement");
            publish(code);

            Course feedback = course("Giving Feedback That Lands", leadershipCat, sara,
                    "A practical model for feedback people can actually act on.",
                    Course.Level.INTERMEDIATE, 90, false, List.of("leadership", "communication"));
            modulesFor(feedback, new String[][]{
                    {"The model", "Situation, behaviour, impact — and what comes after."},
                    {"Difficult conversations", "When the message is genuinely hard."}
            }, new String[][][]{
                    {{"Why most feedback fails", "VIDEO", "660"},
                     {"SBI in practice", "VIDEO", "780"}},
                    {{"Rehearsing the hard one", "VIDEO", "720"},
                     {"Worksheet", "PDF", "300"}}
            });
            feedback.setEnrollmentMode(Course.EnrollmentMode.SELF);
            publish(feedback);

            Course onboarding = course("Engineering Onboarding", engineeringCat, daniel,
                    "Everything a new engineer needs in their first two weeks.",
                    Course.Level.BEGINNER, 120, false, List.of("onboarding"));
            modulesFor(onboarding, new String[][]{
                    {"Your first week", "Environment, access and the deployment pipeline."}
            }, new String[][][]{
                    {{"Local environment setup", "HTML", "600"},
                     {"How we ship", "VIDEO", "900"}}
            });
            // Left in review on purpose so the admin course list shows every state.
            onboarding.setStatus(Course.Status.IN_REVIEW);
            courseRepository.save(onboarding);

            // ---- enrolments ----
            enrolEveryone(security, learners, Instant.now().plus(10, ChronoUnit.DAYS));
            enrolEveryone(code, learners, Instant.now().minus(4, ChronoUnit.DAYS));
            enrol(distributed, arjun, null, 60);
            enrol(distributed, learners.get(1), null, 25);
            enrol(distributed, learners.get(5), null, 100);
            enrol(feedback, learners.get(3), null, 45);
            enrol(feedback, learners.get(6), null, 100);
        });
    }

    private void seedNorthwind(String password) {
        Tenant northwind = tenant("northwind", "Northwind Institute", Tenant.Plan.PRO, 120, Tenant.Status.TRIAL);
        northwind.setTrialEndsAt(Instant.now().plus(18, ChronoUnit.DAYS));
        tenantRepository.save(northwind);
        // A different hue on the same fixed lightness ramp: the whole product
        // re-themes, and contrast still holds without any hand-checking.
        branding(northwind, 158, "0.120", 62,
                "Learning that travels with you.",
                "Professional certification for the maritime and logistics industry.",
                "support@northwind.test");

        inTenant(northwind, () -> {
            OrgUnit root = orgUnit("Northwind Institute", "NW", null);
            OrgUnit faculty = orgUnit("Faculty", "NW-FAC", root);
            OrgUnit students = orgUnit("Students", "NW-STU", root);

            User helena = save(user("helena@northwind.test", "Helena", "Vasquez", password,
                    Set.of(RoleCode.TENANT_ADMIN, RoleCode.INSTRUCTOR), faculty, "Programme Director"));
            User tom = save(user("tom@northwind.test", "Tom", "Ashworth", password,
                    Set.of(RoleCode.LEARNER), students, "Certification candidate"));
            User mira = save(user("mira@northwind.test", "Mira", "Sandberg", password,
                    Set.of(RoleCode.LEARNER), students, "Certification candidate"));

            certificateTemplate("Northwind certification");

            Category certification = category("Certification", "Accredited programmes", "#2E9E7E", 0);

            Course safety = course("Maritime Safety Certification", certification, helena,
                    "The accredited safety programme required before sea service.",
                    Course.Level.INTERMEDIATE, 180, true, List.of("certification", "safety"));
            modulesFor(safety, new String[][]{
                    {"Safety at sea", "Regulations, drills and equipment."},
                    {"Emergency response", "What to do in the first ten minutes."}
            }, new String[][][]{
                    {{"Regulatory framework", "VIDEO", "900"},
                     {"Equipment inspection", "VIDEO", "1080"}},
                    {{"Abandon-ship procedure", "VIDEO", "1200"},
                     {"Final certification exam", "QUIZ", "1800"}}
            });
            quizFor(safety, "Maritime safety certification exam");
            publish(safety);

            enrol(safety, tom, Instant.now().plus(21, ChronoUnit.DAYS), 100);
            enrol(safety, mira, Instant.now().plus(21, ChronoUnit.DAYS), 35);
        });
    }

    // =================================================================
    // Builders
    // =================================================================

    private Tenant tenant(String slug, String name, Tenant.Plan plan, int maxUsers, Tenant.Status status) {
        Tenant tenant = new Tenant(UUID.randomUUID(), slug, name);
        tenant.setPlan(plan);
        tenant.setMaxUsers(maxUsers);
        tenant.setStatus(status);
        tenant.setMaxStorageBytes(50L * 1024 * 1024 * 1024);
        return tenantRepository.save(tenant);
    }

    private void branding(Tenant tenant, int hue, String chroma, int accentHue,
                          String headline, String subtext, String supportEmail) {
        TenantBranding branding = new TenantBranding(tenant.getId());
        branding.setBrandHue(hue);
        branding.setBrandChroma(new BigDecimal(chroma));
        branding.setAccentHue(accentHue);
        branding.setLoginHeadline(headline);
        branding.setLoginSubtext(subtext);
        branding.setSupportEmail(supportEmail);
        branding.setEmailFromName(tenant.getName() + " Learning");
        brandingRepository.save(branding);
    }

    private User user(String email, String firstName, String lastName, String passwordHash,
                      Set<RoleCode> roles, OrgUnit orgUnit, String jobTitle) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(passwordHash);
        user.setRoleSet(roles);
        user.setJobTitle(jobTitle);
        user.setStatus(passwordHash == null ? User.Status.INVITED : User.Status.ACTIVE);
        user.setEmailVerifiedAt(passwordHash == null ? null : Instant.now());
        user.setOrgUnitId(orgUnit == null ? null : orgUnit.getId());
        user.setLastLoginAt(passwordHash == null ? null
                : Instant.now().minus(random.nextInt(72), ChronoUnit.HOURS));
        return user;
    }

    private User save(User user) {
        return userRepository.save(user);
    }

    private OrgUnit orgUnit(String name, String code, OrgUnit parent) {
        OrgUnit unit = new OrgUnit();
        unit.setName(name);
        unit.setCode(code);
        unit.placeUnder(parent);
        return orgUnitRepository.save(unit);
    }

    private Category category(String name, String description, String color, int order) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(Slugs.of(name));
        category.setDescription(description);
        category.setColor(color);
        category.setSortOrder((short) order);
        return categoryRepository.save(category);
    }

    private Course course(String title, Category category, User owner, String summary,
                          Course.Level level, int minutes, boolean mandatory, List<String> tags) {
        Course course = new Course();
        course.setTitle(title);
        course.setSlug(Slugs.of(title));
        course.setSummary(summary);
        course.setDescription("<p>" + summary + "</p>");
        course.setCategoryId(category.getId());
        course.setOwnerId(owner.getId());
        course.setCreatedBy(owner.getId());
        course.setLevel(level);
        course.setEstimatedMinutes(minutes);
        course.setMandatory(mandatory);
        course.setTags(tags.toArray(new String[0]));
        course.setStatus(Course.Status.DRAFT);
        courseRepository.save(course);

        courseInstructorRepository.save(new CourseInstructor(course.getId(), owner.getId()));
        return course;
    }

    private void modulesFor(Course course, String[][] modules, String[][][] lessons) {
        for (int moduleIndex = 0; moduleIndex < modules.length; moduleIndex++) {
            CourseModule module = new CourseModule();
            module.setCourseId(course.getId());
            module.setTitle(modules[moduleIndex][0]);
            module.setSummary(modules[moduleIndex][1]);
            module.setSortOrder((short) moduleIndex);
            moduleRepository.save(module);

            String[][] moduleLessons = lessons[moduleIndex];
            for (int lessonIndex = 0; lessonIndex < moduleLessons.length; lessonIndex++) {
                String[] row = moduleLessons[lessonIndex];
                Lesson lesson = new Lesson();
                lesson.setCourseId(course.getId());
                lesson.setModuleId(module.getId());
                lesson.setTitle(row[0]);
                lesson.setContentType(Lesson.ContentType.valueOf(row[1]));
                lesson.setDurationSeconds(Integer.parseInt(row[2]));
                lesson.setSortOrder((short) lessonIndex);
                lesson.setPreview(moduleIndex == 0 && lessonIndex == 0);
                if (lesson.getContentType() == Lesson.ContentType.HTML) {
                    lesson.setContentHtml(lessonProse(row[0]));
                }
                lessonRepository.save(lesson);
            }
        }
    }

    private String lessonProse(String title) {
        return """
                <p>This lesson walks through the practical decisions behind %s, and the
                trade-offs that only become obvious once a system is under load.</p>
                <h3>What you will take away</h3>
                <ul>
                  <li>The failure modes that actually occur in production.</li>
                  <li>A checklist you can apply to your own service this week.</li>
                  <li>Where the textbook advice stops being useful.</li>
                </ul>
                <blockquote>The interesting question is never whether a component can fail.
                It is what the rest of the system does when it does.</blockquote>
                """.formatted(title.toLowerCase());
    }

    private void quizFor(Course course, String title) {
        Lesson quizLesson = lessonRepository.findByCourse(course.getId()).stream()
                .filter(lesson -> lesson.getContentType() == Lesson.ContentType.QUIZ)
                .findFirst()
                .orElse(null);

        Assessment assessment = new Assessment();
        assessment.setCourseId(course.getId());
        assessment.setLessonId(quizLesson == null ? null : quizLesson.getId());
        assessment.setTitle(title);
        assessment.setDescription("Answer every question. You need "
                + course.getPassingScore() + "% or better to pass.");
        assessment.setPassingScore(course.getPassingScore());
        assessment.setMaxAttempts((short) 3);
        assessment.setTimeLimitMinutes(20);
        assessment.setStatus(Assessment.Status.PUBLISHED);
        assessmentRepository.save(assessment);

        question(assessment, 0, Question.Type.SINGLE_CHOICE,
                "A service must keep accepting writes during a network partition. Which guarantee are you giving up?",
                new String[]{"Strong consistency", "Partition tolerance", "Durability", "Idempotency"}, 0,
                "Under a partition you choose between consistency and availability; accepting writes means relaxing consistency.");

        question(assessment, 1, Question.Type.MULTI_CHOICE,
                "Which of these make a retry safe to perform automatically? Select all that apply.",
                new String[]{"The operation is idempotent", "The request carries an idempotency key",
                        "The client retries immediately with no delay", "The operation charges a card"},
                new int[]{0, 1},
                "Idempotency — inherent or via a key — is what makes a retry safe. Immediate retries amplify load.");

        question(assessment, 2, Question.Type.TRUE_FALSE,
                "Exponential backoff with jitter reduces the chance that retrying clients synchronise into a thundering herd.",
                new String[]{"True", "False"}, 0,
                "Jitter spreads retries across time; without it, clients that failed together retry together.");

        question(assessment, 3, Question.Type.SHORT_ANSWER,
                "What one-word property describes an operation that can be applied repeatedly without changing the result beyond the first application?",
                new String[]{"idempotent", "idempotency", "idempotence"}, new int[]{0, 1, 2},
                "Idempotence is the property that makes at-least-once delivery workable.");

        question(assessment, 4, Question.Type.ESSAY,
                "Describe a failure you have seen (or can imagine) where a retry made an outage worse, and what you would change.",
                new String[]{}, new int[]{},
                "Graded by an instructor.");
    }

    private void question(Assessment assessment, int order, Question.Type type, String prompt,
                          String[] options, int correctIndex, String explanation) {
        question(assessment, order, type, prompt, options, new int[]{correctIndex}, explanation);
    }

    private void question(Assessment assessment, int order, Question.Type type, String prompt,
                          String[] options, int[] correctIndexes, String explanation) {
        Question question = new Question();
        question.setAssessmentId(assessment.getId());
        question.setType(type);
        question.setPrompt(prompt);
        question.setExplanation(explanation);
        question.setPoints(type == Question.Type.ESSAY ? new BigDecimal("4") : BigDecimal.ONE);
        question.setSortOrder((short) order);
        questionRepository.save(question);

        for (int index = 0; index < options.length; index++) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setLabel(options[index]);
            final int current = index;
            option.setCorrect(java.util.Arrays.stream(correctIndexes).anyMatch(i -> i == current));
            option.setSortOrder((short) index);
            optionRepository.save(option);
        }
    }

    private void certificateTemplate(String name) {
        CertificateTemplate template = new CertificateTemplate();
        template.setName(name);
        template.setHtmlTemplate(CertificateRenderer.defaultTemplate());
        template.setDefaultTemplate(true);
        template.setValidityMonths(24);
        certificateTemplateRepository.save(template);
    }

    private void publish(Course course) {
        course.publish(lessonRepository.countByCourse(course.getId()));
        courseRepository.save(course);
    }

    // =================================================================
    // Enrolments and progress
    // =================================================================

    private void enrolEveryone(Course course, List<User> learners, Instant dueAt) {
        for (User learner : learners) {
            // A spread of progress makes dashboards, reports and the "needs
            // attention" panel show something meaningful on first load.
            enrol(course, learner, dueAt, random.nextInt(101));
        }
    }

    private void enrol(Course course, User learner, Instant dueAt, int targetPercent) {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(course.getId());
        enrollment.setUserId(learner.getId());
        enrollment.setDueAt(dueAt);
        enrollment.setSource(course.getEnrollmentMode() == Course.EnrollmentMode.SELF
                ? Enrollment.Source.SELF : Enrollment.Source.MANUAL);
        enrollment.setEnrolledAt(Instant.now().minus(random.nextInt(45) + 1, ChronoUnit.DAYS));
        enrollmentRepository.save(enrollment);

        List<Lesson> lessons = lessonRepository.findByCourse(course.getId());
        if (lessons.isEmpty()) {
            return;
        }

        int completeCount = (int) Math.round(lessons.size() * targetPercent / 100.0);
        for (int index = 0; index < completeCount; index++) {
            Lesson lesson = lessons.get(index);
            LessonProgress progress = new LessonProgress();
            progress.setEnrollmentId(enrollment.getId());
            progress.setLessonId(lesson.getId());
            progress.setUserId(learner.getId());
            progress.setSecondsWatched(lesson.getDurationSeconds());
            progress.setLastPositionSeconds(lesson.getDurationSeconds());
            progress.complete();
            progress.setUpdatedAt(Instant.now().minus(random.nextInt(14), ChronoUnit.DAYS));
            progressRepository.save(progress);
        }

        // One lesson partially watched, so "continue learning" has a real resume point.
        if (completeCount < lessons.size() && targetPercent > 0) {
            Lesson current = lessons.get(completeCount);
            LessonProgress progress = new LessonProgress();
            progress.setEnrollmentId(enrollment.getId());
            progress.setLessonId(current.getId());
            progress.setUserId(learner.getId());
            progress.setStatus(LessonProgress.Status.IN_PROGRESS);
            progress.setSecondsWatched(current.getDurationSeconds() / 3);
            progress.setLastPositionSeconds(current.getDurationSeconds() / 3);
            progressRepository.save(progress);
        }

        int actualPercent = (int) Math.round(completeCount * 100.0 / lessons.size());
        enrollment.setProgressPercent((short) actualPercent);
        enrollment.setLastAccessedAt(targetPercent == 0 ? null
                : Instant.now().minus(random.nextInt(10), ChronoUnit.DAYS));

        if (actualPercent >= 100) {
            enrollment.markCompleted();
            enrollment.setCompletedAt(Instant.now().minus(random.nextInt(20) + 1, ChronoUnit.DAYS));
        }
        enrollmentRepository.save(enrollment);

        if (enrollment.getStatus() == Enrollment.Status.COMPLETED && course.isCertificateEnabled()) {
            certificateIssuer.issueFor(enrollment.getId());
        }
    }

    // =================================================================

    private void inTenant(Tenant tenant, Runnable body) {
        executor.runAs(new TenantContext.Snapshot(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.isSystemTenant()), body);
    }
}
