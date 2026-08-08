package com.learnnexus.scheduling;

import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.enrollment.Enrollment;
import com.learnnexus.enrollment.EnrollmentRepositories;
import com.learnnexus.iam.RefreshTokenRepository;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.notification.MailService;
import com.learnnexus.notification.NotificationService;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Background maintenance.
 *
 * <p>Scheduled work has no HTTP request to derive a tenant from, so each job
 * iterates operational tenants explicitly and runs its body inside
 * {@link TenantContext#runAs}. Without that, every tenant-scoped query would
 * resolve to the no-tenant sentinel and quietly do nothing.
 *
 * <p>Single-node schedules. Running more than one instance needs a lock —
 * ShedLock over the existing Redis connection is the intended route — otherwise
 * learners get duplicate reminder emails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private static final Duration REMINDER_WINDOW = Duration.ofDays(3);

    private final TenantRepository tenantRepository;
    private final EnrollmentRepositories.EnrollmentRepository enrollmentRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final JdbcTemplate jdbcTemplate;

    /** Reminds learners about mandatory training falling due in the next three days. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void sendDeadlineReminders() {
        forEachOperationalTenant(snapshot -> {
            Instant now = Instant.now();
            List<Enrollment> due = enrollmentRepository.findDueBetween(now, now.plus(REMINDER_WINDOW));

            for (Enrollment enrollment : due) {
                courseRepository.findActiveById(enrollment.getCourseId()).ifPresent(course ->
                        userRepository.findActiveById(enrollment.getUserId()).ifPresent(learner -> {
                            String dueDescription = course.getTitle() + " is due by "
                                    + enrollment.getDueAt().atZone(ZoneOffset.UTC).toLocalDate() + ".";
                            notificationService.deadlineApproaching(
                                    learner.getId(), course, enrollment.getDueAt());
                            mailService.sendDeadlineReminder(learner, snapshot, course.getTitle(),
                                    course.getId().toString(), dueDescription);
                        }));
            }
            if (!due.isEmpty()) {
                log.info("Sent {} deadline reminders for tenant {}", due.size(), snapshot.slug());
            }
        });
    }

    /** Closes enrolments whose access window has passed. */
    @Scheduled(cron = "0 15 1 * * *", zone = "UTC")
    @Transactional
    public void expireEnrollments() {
        int expired = jdbcTemplate.update("""
                update enrollments set status = 'EXPIRED'
                where status = 'ACTIVE' and expires_at is not null and expires_at < now()
                """);
        if (expired > 0) {
            log.info("Expired {} lapsed enrolments", expired);
        }
    }

    /**
     * Submits attempts abandoned past their time limit, so they stop occupying
     * the learner's remaining attempts and appear correctly in reports.
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "UTC")
    @Transactional
    public void closeAbandonedAttempts() {
        int closed = jdbcTemplate.update("""
                update attempts
                set status = 'EXPIRED', submitted_at = now()
                where status = 'IN_PROGRESS' and expires_at is not null and expires_at < now()
                """);
        if (closed > 0) {
            log.info("Closed {} abandoned assessment attempts", closed);
        }
    }

    /** Clears refresh tokens that expired more than a month ago. */
    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    @Transactional
    public void pruneRefreshTokens() {
        int removed = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(30)));
        if (removed > 0) {
            log.info("Pruned {} expired refresh tokens", removed);
        }
    }

    /** Suspends tenants whose trial has lapsed without converting. */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void suspendLapsedTrials() {
        int suspended = jdbcTemplate.update("""
                update tenants set status = 'SUSPENDED', updated_at = now()
                where status = 'TRIAL' and trial_ends_at is not null and trial_ends_at < now()
                  and system_tenant = false and deleted_at is null
                """);
        if (suspended > 0) {
            log.info("Suspended {} lapsed trial tenants", suspended);
        }
    }

    private void forEachOperationalTenant(java.util.function.Consumer<TenantContext.Snapshot> body) {
        for (Tenant tenant : tenantRepository.findAll()) {
            if (!tenant.isOperational() || tenant.isSystemTenant()) {
                continue;
            }
            TenantContext.Snapshot snapshot = new TenantContext.Snapshot(
                    tenant.getId(), tenant.getSlug(), tenant.getName(), false);
            try {
                TenantContext.runAs(snapshot, () -> body.accept(snapshot));
            } catch (RuntimeException ex) {
                // One tenant's bad data must not stop the sweep for everyone else.
                log.error("Scheduled job failed for tenant {}: {}", tenant.getSlug(), ex.getMessage(), ex);
            }
        }
    }
}
