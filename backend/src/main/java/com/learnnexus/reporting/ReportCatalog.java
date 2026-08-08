package com.learnnexus.reporting;

import java.util.List;

/**
 * The catalogue of standard reports.
 *
 * <p>Each report is a named SQL statement with a fixed column list, which lets a
 * single generic endpoint, a single table component and a single CSV exporter
 * serve every report. Adding a report is a data change here rather than a new
 * controller, service and DTO triple.
 *
 * <p>Bind order varies between statements — a filter attached to a {@code LEFT
 * JOIN} has to appear before the {@code WHERE} that carries the tenant — so each
 * report declares its own {@link Slot} sequence rather than relying on a shared
 * convention that would silently misbind. {@code ReportService} asserts that
 * {@link Slot#TENANT} appears exactly once in every sequence.
 *
 * <p>Optional filters are written as
 * {@code (cast(? as X) is null or column = cast(? as X))}, which is the portable
 * way to express "ignore this filter when unset" in plain JDBC, and is why the
 * optional slots appear twice.
 */
public enum ReportCatalog {

    // Enum constants are initialised before the enum's own static fields, so the
    // shared slot sequences live in a nested holder that is loaded on first use.

    COURSE_COMPLETION(
            "course-completion",
            "Course completion",
            "Enrolment, completion and average progress for every course.",
            List.of(
                    new Column("course", "Course", Column.Type.TEXT),
                    new Column("category", "Category", Column.Type.TEXT),
                    new Column("status", "Status", Column.Type.TEXT),
                    new Column("enrolled", "Enrolled", Column.Type.NUMBER),
                    new Column("completed", "Completed", Column.Type.NUMBER),
                    new Column("in_progress", "In progress", Column.Type.NUMBER),
                    new Column("overdue", "Overdue", Column.Type.NUMBER),
                    new Column("completion_rate", "Completion %", Column.Type.PERCENT),
                    new Column("avg_progress", "Avg progress %", Column.Type.PERCENT)),
            Slots.JOIN_FILTERED,
            """
            select c.title                                                            as course,
                   coalesce(cat.name, '—')                                            as category,
                   c.status                                                           as status,
                   count(e.id)                                                        as enrolled,
                   count(e.id) filter (where e.status = 'COMPLETED')                  as completed,
                   count(e.id) filter (where e.status = 'ACTIVE')                     as in_progress,
                   count(e.id) filter (where e.status = 'ACTIVE' and e.due_at < now()) as overdue,
                   case when count(e.id) = 0 then 0
                        else round(count(e.id) filter (where e.status = 'COMPLETED') * 100.0 / count(e.id))
                   end                                                                as completion_rate,
                   coalesce(round(avg(e.progress_percent)), 0)                        as avg_progress
            from courses c
            left join categories cat on cat.id = c.category_id and cat.tenant_id = c.tenant_id
            left join enrollments e  on e.course_id = c.id and e.tenant_id = c.tenant_id
                                    and (cast(? as timestamptz) is null or e.enrolled_at >= cast(? as timestamptz))
                                    and (cast(? as timestamptz) is null or e.enrolled_at <= cast(? as timestamptz))
            left join users u        on u.id = e.user_id and u.tenant_id = e.tenant_id
            left join org_units o    on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            where c.tenant_id = ? and c.deleted_at is null
              and (cast(? as uuid) is null or c.id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by c.id, c.title, cat.name, c.status
            order by enrolled desc, c.title
            """),

    LEARNER_PROGRESS(
            "learner-progress",
            "Learner progress",
            "Every learner's assigned, completed and overdue courses.",
            List.of(
                    new Column("learner", "Learner", Column.Type.TEXT),
                    new Column("email", "Email", Column.Type.TEXT),
                    new Column("org_unit", "Department", Column.Type.TEXT),
                    new Column("assigned", "Assigned", Column.Type.NUMBER),
                    new Column("completed", "Completed", Column.Type.NUMBER),
                    new Column("overdue", "Overdue", Column.Type.NUMBER),
                    new Column("avg_progress", "Avg progress %", Column.Type.PERCENT),
                    new Column("learning_hours", "Learning hours", Column.Type.NUMBER),
                    new Column("last_active", "Last active", Column.Type.DATE)),
            Slots.JOIN_FILTERED,
            """
            select trim(u.first_name || ' ' || coalesce(u.last_name, ''))              as learner,
                   u.email                                                            as email,
                   coalesce(o.name, '—')                                              as org_unit,
                   count(e.id)                                                        as assigned,
                   count(e.id) filter (where e.status = 'COMPLETED')                  as completed,
                   count(e.id) filter (where e.status = 'ACTIVE' and e.due_at < now()) as overdue,
                   coalesce(round(avg(e.progress_percent)), 0)                        as avg_progress,
                   coalesce(round((select sum(lp.seconds_watched) from lesson_progress lp
                                    where lp.tenant_id = u.tenant_id and lp.user_id = u.id) / 3600.0, 1), 0)
                                                                                      as learning_hours,
                   max(e.last_accessed_at)                                            as last_active
            from users u
            left join org_units o   on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            left join enrollments e on e.user_id = u.id and e.tenant_id = u.tenant_id
                                   and (cast(? as timestamptz) is null or e.enrolled_at >= cast(? as timestamptz))
                                   and (cast(? as timestamptz) is null or e.enrolled_at <= cast(? as timestamptz))
            where u.tenant_id = ? and u.deleted_at is null
              and (cast(? as uuid) is null or e.course_id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by u.id, u.first_name, u.last_name, u.email, o.name, u.tenant_id
            order by overdue desc, assigned desc, learner
            """),

    ASSESSMENT_SCORES(
            "assessment-scores",
            "Assessment scores",
            "Pass rates and score distribution for every published assessment.",
            List.of(
                    new Column("assessment", "Assessment", Column.Type.TEXT),
                    new Column("course", "Course", Column.Type.TEXT),
                    new Column("attempts", "Attempts", Column.Type.NUMBER),
                    new Column("learners", "Learners", Column.Type.NUMBER),
                    new Column("passed", "Passed", Column.Type.NUMBER),
                    new Column("pass_rate", "Pass rate %", Column.Type.PERCENT),
                    new Column("avg_score", "Avg score %", Column.Type.PERCENT),
                    new Column("awaiting_grading", "Awaiting grading", Column.Type.NUMBER)),
            Slots.JOIN_FILTERED,
            """
            select s.title                                                       as assessment,
                   c.title                                                       as course,
                   count(a.id)                                                   as attempts,
                   count(distinct a.user_id)                                     as learners,
                   count(a.id) filter (where a.passed)                           as passed,
                   case when count(a.id) = 0 then 0
                        else round(count(a.id) filter (where a.passed) * 100.0 / count(a.id))
                   end                                                           as pass_rate,
                   coalesce(round(avg(a.percentage)), 0)                         as avg_score,
                   count(a.id) filter (where a.requires_grading
                                         and a.status = 'SUBMITTED')             as awaiting_grading
            from assessments s
            join courses c on c.id = s.course_id and c.tenant_id = s.tenant_id
            left join attempts a on a.assessment_id = s.id and a.tenant_id = s.tenant_id
                                and a.status in ('SUBMITTED','GRADED')
                                and (cast(? as timestamptz) is null or a.submitted_at >= cast(? as timestamptz))
                                and (cast(? as timestamptz) is null or a.submitted_at <= cast(? as timestamptz))
            left join users u     on u.id = a.user_id and u.tenant_id = a.tenant_id
            left join org_units o on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            where s.tenant_id = ?
              and (cast(? as uuid) is null or s.course_id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by s.id, s.title, c.title
            order by attempts desc, assessment
            """),

    COMPLIANCE_OVERDUE(
            "compliance-overdue",
            "Compliance & overdue",
            "Mandatory training past its deadline, worst first.",
            List.of(
                    new Column("learner", "Learner", Column.Type.TEXT),
                    new Column("email", "Email", Column.Type.TEXT),
                    new Column("org_unit", "Department", Column.Type.TEXT),
                    new Column("course", "Course", Column.Type.TEXT),
                    new Column("due_at", "Due", Column.Type.DATE),
                    new Column("days_overdue", "Days overdue", Column.Type.NUMBER),
                    new Column("progress", "Progress %", Column.Type.PERCENT),
                    new Column("mandatory", "Mandatory", Column.Type.TEXT)),
            Slots.WHERE_FILTERED,
            """
            select trim(u.first_name || ' ' || coalesce(u.last_name, ''))  as learner,
                   u.email                                                as email,
                   coalesce(o.name, '—')                                  as org_unit,
                   c.title                                                as course,
                   e.due_at                                               as due_at,
                   greatest(0, extract(day from now() - e.due_at))::int    as days_overdue,
                   e.progress_percent                                     as progress,
                   case when c.is_mandatory then 'Yes' else 'No' end      as mandatory
            from enrollments e
            join users u   on u.id = e.user_id and u.tenant_id = e.tenant_id
            join courses c on c.id = e.course_id and c.tenant_id = e.tenant_id
            left join org_units o on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            where e.tenant_id = ?
              and e.status = 'ACTIVE' and e.due_at is not null and e.due_at < now()
              and u.deleted_at is null
              and (cast(? as timestamptz) is null or e.due_at >= cast(? as timestamptz))
              and (cast(? as timestamptz) is null or e.due_at <= cast(? as timestamptz))
              and (cast(? as uuid) is null or e.course_id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            order by days_overdue desc, learner
            """),

    ENROLLMENT_TRENDS(
            "enrollment-trends",
            "Enrolment trends",
            "New enrolments and completions week by week.",
            List.of(
                    new Column("week", "Week beginning", Column.Type.DATE),
                    new Column("enrolled", "Enrolled", Column.Type.NUMBER),
                    new Column("completed", "Completed", Column.Type.NUMBER)),
            Slots.WHERE_FILTERED,
            """
            select date_trunc('week', e.enrolled_at)                as week,
                   count(*)                                        as enrolled,
                   count(*) filter (where e.status = 'COMPLETED')   as completed
            from enrollments e
            left join users u     on u.id = e.user_id and u.tenant_id = e.tenant_id
            left join org_units o on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            where e.tenant_id = ?
              and (cast(? as timestamptz) is null or e.enrolled_at >= cast(? as timestamptz))
              and (cast(? as timestamptz) is null or e.enrolled_at <= cast(? as timestamptz))
              and (cast(? as uuid) is null or e.course_id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by week
            order by week
            """),

    LEARNING_HOURS(
            "learning-hours",
            "Learning hours",
            "Time spent per department, and how much of it converts to completions.",
            List.of(
                    new Column("org_unit", "Department", Column.Type.TEXT),
                    new Column("learners", "Learners", Column.Type.NUMBER),
                    new Column("learning_hours", "Learning hours", Column.Type.NUMBER),
                    new Column("completions", "Completions", Column.Type.NUMBER)),
            Slots.WHERE_FILTERED,
            """
            select coalesce(o.name, 'Unassigned')                                        as org_unit,
                   count(distinct lp.user_id)                                            as learners,
                   round(coalesce(sum(lp.seconds_watched), 0) / 3600.0, 1)               as learning_hours,
                   (select count(*) from enrollments e2
                     join users u2 on u2.id = e2.user_id and u2.tenant_id = e2.tenant_id
                    where e2.tenant_id = lp.tenant_id and e2.status = 'COMPLETED'
                      and coalesce(u2.org_unit_id::text, 'none') = coalesce(o.id::text, 'none')) as completions
            from lesson_progress lp
            join users u          on u.id = lp.user_id and u.tenant_id = lp.tenant_id
            left join org_units o on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            left join enrollments e on e.id = lp.enrollment_id and e.tenant_id = lp.tenant_id
            where lp.tenant_id = ?
              and (cast(? as timestamptz) is null or lp.updated_at >= cast(? as timestamptz))
              and (cast(? as timestamptz) is null or lp.updated_at <= cast(? as timestamptz))
              and (cast(? as uuid) is null or e.course_id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by o.id, o.name, lp.tenant_id
            order by learning_hours desc
            """),

    INSTRUCTOR_ACTIVITY(
            "instructor-activity",
            "Instructor activity",
            "Courses owned, learners reached and grading turnaround per instructor.",
            List.of(
                    new Column("instructor", "Instructor", Column.Type.TEXT),
                    new Column("email", "Email", Column.Type.TEXT),
                    new Column("courses", "Courses", Column.Type.NUMBER),
                    new Column("learners", "Learners", Column.Type.NUMBER),
                    new Column("graded", "Attempts graded", Column.Type.NUMBER),
                    new Column("awaiting", "Awaiting grading", Column.Type.NUMBER)),
            Slots.JOIN_FILTERED,
            """
            select trim(u.first_name || ' ' || coalesce(u.last_name, ''))              as instructor,
                   u.email                                                            as email,
                   count(distinct c.id)                                               as courses,
                   count(distinct e.user_id)                                          as learners,
                   (select count(*) from attempts a
                     where a.tenant_id = u.tenant_id and a.graded_by = u.id)          as graded,
                   (select count(*) from attempts a
                      join assessments s on s.id = a.assessment_id and s.tenant_id = a.tenant_id
                     where a.tenant_id = u.tenant_id and a.requires_grading
                       and a.status = 'SUBMITTED'
                       and s.course_id in (select ci.course_id from course_instructors ci
                                            where ci.tenant_id = u.tenant_id and ci.user_id = u.id))
                                                                                      as awaiting
            from users u
            left join course_instructors ci on ci.user_id = u.id and ci.tenant_id = u.tenant_id
            left join courses c on (c.id = ci.course_id or c.owner_id = u.id)
                               and c.tenant_id = u.tenant_id and c.deleted_at is null
            left join enrollments e on e.course_id = c.id and e.tenant_id = c.tenant_id
                                   and (cast(? as timestamptz) is null or e.enrolled_at >= cast(? as timestamptz))
                                   and (cast(? as timestamptz) is null or e.enrolled_at <= cast(? as timestamptz))
            left join org_units o on o.id = u.org_unit_id and o.tenant_id = u.tenant_id
            where u.tenant_id = ? and u.deleted_at is null
              and (u.roles && array['INSTRUCTOR','AUTHOR']::text[])
              and (cast(? as uuid) is null or c.id = cast(? as uuid))
              and (cast(? as text) is null or (o.path || o.id::text || '/') like cast(? as text) || '%')
            group by u.id, u.first_name, u.last_name, u.email, u.tenant_id
            order by learners desc, instructor
            """);

    /** One column of a report result, carrying enough type information to format it. */
    public record Column(String key, String label, Type type) {
        public enum Type { TEXT, NUMBER, PERCENT, DATE }
    }

    /** Shared bind sequences, in a holder so they exist before the constants use them. */
    private static final class Slots {

        /** Filters attached to a LEFT JOIN, so they bind before the tenant in the WHERE. */
        static final List<Slot> JOIN_FILTERED =
                List.of(Slot.FROM, Slot.FROM, Slot.TO, Slot.TO, Slot.TENANT,
                        Slot.COURSE, Slot.COURSE, Slot.ORG_PATH, Slot.ORG_PATH);

        /** Filters entirely inside the WHERE, so the tenant binds first. */
        static final List<Slot> WHERE_FILTERED =
                List.of(Slot.TENANT, Slot.FROM, Slot.FROM, Slot.TO, Slot.TO,
                        Slot.COURSE, Slot.COURSE, Slot.ORG_PATH, Slot.ORG_PATH);

        private Slots() {
        }
    }

    /** A value a report's SQL binds, in the order the statement expects it. */
    public enum Slot { TENANT, FROM, TO, COURSE, ORG_PATH }

    private final String key;
    private final String title;
    private final String description;
    private final List<Column> columns;
    private final String sql;
    private final List<Slot> slots;

    ReportCatalog(String key, String title, String description, List<Column> columns,
                  List<Slot> slots, String sql) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.columns = columns;
        this.slots = slots;
        this.sql = sql;
    }

    public List<Slot> slots() {
        return slots;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public List<Column> columns() {
        return columns;
    }

    public String sql() {
        return sql;
    }

    public static ReportCatalog byKey(String key) {
        for (ReportCatalog report : values()) {
            if (report.key.equalsIgnoreCase(key)) {
                return report;
            }
        }
        throw new IllegalArgumentException("Unknown report: " + key);
    }
}
