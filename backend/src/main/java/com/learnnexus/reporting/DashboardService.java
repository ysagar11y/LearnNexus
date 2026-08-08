package com.learnnexus.reporting;

import com.learnnexus.common.TenantAwareJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The admin console's landing figures, gathered in a handful of round trips. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TenantAwareJdbc jdbc;

    public record AdminDashboard(
            Headline headline,
            List<TrendPoint> activity,
            List<CourseRow> topCourses,
            List<CourseRow> needsAttention,
            List<ActivityItem> recentActivity,
            int awaitingGrading
    ) {}

    public record Headline(
            long activeLearners,
            long publishedCourses,
            long enrolments,
            long completions,
            long overdue,
            long certificates,
            int completionRate,
            long learningHours,
            /** Change in completions versus the preceding period of equal length. */
            int completionsDelta
    ) {}

    public record TrendPoint(Instant week, long enrolled, long completed) {}

    public record CourseRow(
            UUID courseId,
            String title,
            long enrolled,
            long completed,
            int completionRate,
            int averageProgress,
            long overdue
    ) {}

    public record ActivityItem(
            String action,
            String summary,
            String actorEmail,
            Instant at
    ) {}

    @Transactional(readOnly = true)
    public AdminDashboard adminDashboard() {
        return new AdminDashboard(
                headline(), activity(), topCourses(), needsAttention(),
                recentActivity(), awaitingGrading());
    }

    private Headline headline() {
        return jdbc.queryOne("""
                select
                  (select count(*) from users u
                    where u.tenant_id = t.id and u.deleted_at is null and u.status = 'ACTIVE')   as active_learners,
                  (select count(*) from courses c
                    where c.tenant_id = t.id and c.deleted_at is null and c.status = 'PUBLISHED') as published_courses,
                  (select count(*) from enrollments e where e.tenant_id = t.id)                   as enrolments,
                  (select count(*) from enrollments e
                    where e.tenant_id = t.id and e.status = 'COMPLETED')                          as completions,
                  (select count(*) from enrollments e
                    where e.tenant_id = t.id and e.status = 'ACTIVE' and e.due_at < now())        as overdue,
                  (select count(*) from certificates c
                    where c.tenant_id = t.id and c.revoked_at is null)                            as certificates,
                  (select coalesce(round(sum(lp.seconds_watched) / 3600.0), 0) from lesson_progress lp
                    where lp.tenant_id = t.id)                                                    as learning_hours,
                  (select count(*) from enrollments e
                    where e.tenant_id = t.id and e.completed_at >= now() - interval '30 days')    as completions_recent,
                  (select count(*) from enrollments e
                    where e.tenant_id = t.id and e.completed_at >= now() - interval '60 days'
                      and e.completed_at < now() - interval '30 days')                            as completions_previous
                from tenants t
                where t.id = ?
                """, (rs, rowNum) -> {
            long enrolments = rs.getLong("enrolments");
            long completions = rs.getLong("completions");
            long recent = rs.getLong("completions_recent");
            long previous = rs.getLong("completions_previous");

            int rate = enrolments == 0 ? 0 : (int) Math.round(completions * 100.0 / enrolments);
            // Percentage change is undefined against a zero baseline; report the
            // raw count as the delta instead of a misleading "+100%".
            int delta = previous == 0
                    ? (int) recent
                    : (int) Math.round((recent - previous) * 100.0 / previous);

            return new Headline(
                    rs.getLong("active_learners"), rs.getLong("published_courses"), enrolments,
                    completions, rs.getLong("overdue"), rs.getLong("certificates"),
                    rate, rs.getLong("learning_hours"), delta);
        }).orElse(new Headline(0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    private List<TrendPoint> activity() {
        // generate_series keeps empty weeks in the result so the chart shows a
        // real gap rather than joining across missing data.
        return jdbc.query("""
                with weeks as (
                    select generate_series(
                        date_trunc('week', now() - interval '11 weeks'),
                        date_trunc('week', now()),
                        interval '1 week') as week
                )
                select w.week,
                       count(e.id) filter (where date_trunc('week', e.enrolled_at) = w.week)  as enrolled,
                       count(e.id) filter (where date_trunc('week', e.completed_at) = w.week) as completed
                from weeks w
                left join enrollments e on e.tenant_id = ?
                     and (date_trunc('week', e.enrolled_at) = w.week
                       or date_trunc('week', e.completed_at) = w.week)
                group by w.week
                order by w.week
                """, (rs, rowNum) -> new TrendPoint(
                rs.getTimestamp("week").toInstant(),
                rs.getLong("enrolled"),
                rs.getLong("completed")));
    }

    private List<CourseRow> topCourses() {
        return courseRows("""
                order by enrolled desc, completion_rate desc
                limit 5
                """);
    }

    private List<CourseRow> needsAttention() {
        return courseRows("""
                having count(e.id) filter (where e.status = 'ACTIVE' and e.due_at < now()) > 0
                    or (count(e.id) >= 3 and coalesce(round(avg(e.progress_percent)), 0) < 35)
                order by overdue desc, avg_progress
                limit 5
                """);
    }

    private List<CourseRow> courseRows(String tail) {
        return jdbc.query("""
                select c.id,
                       c.title,
                       count(e.id)                                                        as enrolled,
                       count(e.id) filter (where e.status = 'COMPLETED')                  as completed,
                       case when count(e.id) = 0 then 0
                            else round(count(e.id) filter (where e.status = 'COMPLETED') * 100.0 / count(e.id))
                       end                                                                as completion_rate,
                       coalesce(round(avg(e.progress_percent)), 0)                        as avg_progress,
                       count(e.id) filter (where e.status = 'ACTIVE' and e.due_at < now()) as overdue
                from courses c
                left join enrollments e on e.course_id = c.id and e.tenant_id = c.tenant_id
                where c.tenant_id = ? and c.deleted_at is null and c.status = 'PUBLISHED'
                group by c.id, c.title
                """ + tail, (rs, rowNum) -> new CourseRow(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getLong("enrolled"),
                rs.getLong("completed"),
                rs.getInt("completion_rate"),
                rs.getInt("avg_progress"),
                rs.getLong("overdue")));
    }

    private List<ActivityItem> recentActivity() {
        return jdbc.query("""
                select action, summary, actor_email, created_at
                from audit_logs
                where tenant_id = ?
                order by created_at desc
                limit 12
                """, (rs, rowNum) -> new ActivityItem(
                rs.getString("action"),
                rs.getString("summary"),
                rs.getString("actor_email"),
                rs.getTimestamp("created_at").toInstant()));
    }

    private int awaitingGrading() {
        return (int) jdbc.queryForLong("""
                select count(*) from attempts
                where tenant_id = ? and requires_grading = true and status = 'SUBMITTED'
                """);
    }
}
