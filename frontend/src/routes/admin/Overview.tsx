import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { StatTile } from '@ds/components/core/StatTile';
import { Progress } from '@ds/components/core/Progress';
import { Button } from '@ds/components/forms/Button';
import { Badge } from '@ds/components/feedback/Badge';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api } from '@/lib/api';
import { formatNumber, humanise, relativeTime } from '@/lib/format';
import type { AdminDashboard } from '@/lib/types';
import { ErrorState, LoadingRows, PageHeader } from '@/components/states';
import {
  IconCertificate,
  IconChart,
  IconClock,
  IconCourses,
  IconGrading,
  IconPeople,
  IconPlus,
} from '@/components/icons';

export default function AdminOverview() {
  const navigate = useNavigate();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: () => api.get<AdminDashboard>('/reports/dashboard'),
  });

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  const headline = data?.headline;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Workspace overview"
        subtitle="How learning is going across your organisation."
        actions={
          <>
            <Button variant="outline" onClick={() => navigate('/admin/reports')}>
              <IconChart size={15} />
              Reports
            </Button>
            <Button onClick={() => navigate('/admin/courses')}>
              <IconPlus size={15} />
              New course
            </Button>
          </>
        }
      />

      <div className="stat-grid">
        <StatTile
          label="Active learners"
          value={headline ? formatNumber(headline.activeLearners) : '—'}
          icon={<IconPeople size={15} />}
        />
        <StatTile
          label="Published courses"
          value={headline ? formatNumber(headline.publishedCourses) : '—'}
          icon={<IconCourses size={15} />}
        />
        <StatTile
          label="Completion rate"
          value={headline?.completionRate ?? '—'}
          unit="%"
          delta={headline?.completionsDelta}
          deltaTone="positive"
          caption={headline ? `${formatNumber(headline.completions)} completions` : undefined}
          icon={<IconChart size={15} />}
        />
        <StatTile
          label="Overdue"
          value={headline ? formatNumber(headline.overdue) : '—'}
          // Rising overdue counts are bad news, so the tone is stated explicitly.
          deltaTone="negative"
          caption={headline?.overdue ? 'Chase these' : 'All on track'}
          icon={<IconClock size={15} />}
        />
        <StatTile
          label="Learning hours"
          value={headline ? formatNumber(headline.learningHours) : '—'}
          icon={<IconClock size={15} />}
        />
        <StatTile
          label="Certificates"
          value={headline ? formatNumber(headline.certificates) : '—'}
          icon={<IconCertificate size={15} />}
        />
      </div>

      {data && data.awaitingGrading > 0 && (
        <div className="ln-panel section-gap" style={{ padding: 'var(--space-5)' }}>
          <div className="row row-gap-3 row-wrap">
            <IconGrading size={20} />
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}>
                {data.awaitingGrading} {data.awaitingGrading === 1 ? 'submission' : 'submissions'} waiting to be graded
              </div>
              <div style={{ fontSize: 'var(--text-xs)', opacity: 0.82 }}>
                Learners cannot finish these courses until someone grades them.
              </div>
            </div>
            <Button size="sm" onClick={() => navigate('/admin/grading')}>
              Open grading queue
            </Button>
          </div>
        </div>
      )}

      <div className="columns section-gap">
        <div className="stack stack-4">
          <ActivityChart points={data?.activity ?? []} loading={isLoading} />

          <section className="surface">
            <div className="surface-header">
              <h2>Needs attention</h2>
              <Link to="/admin/reports" className="link">
                Full report
              </Link>
            </div>
            {isLoading ? (
              <LoadingRows rows={3} />
            ) : data && data.needsAttention.length > 0 ? (
              data.needsAttention.map((course) => (
                <Link
                  key={course.courseId}
                  to={`/admin/courses/${course.courseId}`}
                  className="surface-row"
                  style={{ textDecoration: 'none', color: 'inherit' }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="truncate" style={{ fontWeight: 500, fontSize: 'var(--text-sm)' }}>
                      {course.title}
                    </div>
                    <div className="meta" style={{ marginTop: 3 }}>
                      <span>{course.enrolled} enrolled</span>
                      <span className="meta-dot" />
                      <span>{course.completionRate}% complete</span>
                      {course.overdue > 0 && (
                        <>
                          <span className="meta-dot" />
                          <span style={{ color: 'var(--destructive)' }}>{course.overdue} overdue</span>
                        </>
                      )}
                    </div>
                  </div>
                  <div style={{ width: 110 }}>
                    <Progress value={course.averageProgress} size="xs" />
                  </div>
                </Link>
              ))
            ) : (
              <div style={{ padding: 24 }}>
                <EmptyState
                  compact
                  title="Everything is on track"
                  description="No course has overdue learners or stalled progress."
                  action={
                    <Button size="sm" variant="outline" onClick={() => navigate('/admin/reports')}>
                      See the full picture
                    </Button>
                  }
                />
              </div>
            )}
          </section>
        </div>

        <aside className="stack stack-4">
          <section className="surface">
            <div className="surface-header">
              <h2>Most enrolled</h2>
            </div>
            {data?.topCourses.map((course, index) => (
              <Link
                key={course.courseId}
                to={`/admin/courses/${course.courseId}`}
                className="surface-row"
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <span
                  aria-hidden="true"
                  style={{
                    fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
                    fontSize: 'var(--text-lg)',
                    color: 'var(--muted-foreground)',
                    width: 18,
                  }}
                >
                  {index + 1}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="truncate" style={{ fontSize: 'var(--text-sm)', fontWeight: 500 }}>
                    {course.title}
                  </div>
                  <div className="meta" style={{ marginTop: 2 }}>
                    <span>{course.enrolled} enrolled</span>
                  </div>
                </div>
                <Badge tone={course.completionRate >= 60 ? 'success' : 'neutral'} size="sm">
                  {course.completionRate}%
                </Badge>
              </Link>
            ))}
          </section>

          <section className="surface">
            <div className="surface-header">
              <h2>Recent activity</h2>
              <Link to="/admin/audit" className="link">
                Audit trail
              </Link>
            </div>
            {data?.recentActivity.slice(0, 8).map((entry, index) => (
              <div key={index} className="surface-row" style={{ alignItems: 'flex-start', padding: '11px 18px' }}>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontSize: 'var(--text-sm)' }}>
                    {entry.summary ?? humanise(entry.action)}
                  </div>
                  <div className="meta" style={{ marginTop: 2 }}>
                    {entry.actorEmail && (
                      <>
                        <span className="truncate" style={{ maxWidth: 150 }}>
                          {entry.actorEmail}
                        </span>
                        <span className="meta-dot" />
                      </>
                    )}
                    <span>{relativeTime(entry.at)}</span>
                  </div>
                </div>
              </div>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}

/**
 * Twelve weeks of enrolments and completions.
 *
 * Drawn as inline SVG rather than pulling in a charting library: two series over
 * twelve points does not justify 40 kB of JavaScript, and the bars inherit the
 * tenant's palette from the same tokens as everything else.
 */
function ActivityChart({
  points,
  loading,
}: {
  points: Array<{ week: string; enrolled: number; completed: number }>;
  loading: boolean;
}) {
  const max = Math.max(1, ...points.map((point) => Math.max(point.enrolled, point.completed)));

  return (
    <section className="surface" style={{ padding: 18 }}>
      <div className="row row-gap-3" style={{ marginBottom: 16 }}>
        <h2 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semibold)' }}>
          Activity
        </h2>
        <div className="spacer" />
        <span className="row row-gap-2" style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
          <span
            aria-hidden="true"
            style={{ width: 9, height: 9, borderRadius: 2, background: 'var(--chart-1)' }}
          />
          Enrolled
        </span>
        <span className="row row-gap-2" style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
          <span
            aria-hidden="true"
            style={{ width: 9, height: 9, borderRadius: 2, background: 'var(--success)' }}
          />
          Completed
        </span>
      </div>

      {loading ? (
        <div style={{ height: 150 }} />
      ) : (
        <div
          role="img"
          aria-label={`Weekly enrolments and completions over the last ${points.length} weeks`}
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            gap: 8,
            height: 150,
          }}
        >
          {points.map((point) => (
            <div
              key={point.week}
              style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}
              title={`Week of ${new Date(point.week).toLocaleDateString('en-GB')}: ${point.enrolled} enrolled, ${point.completed} completed`}
            >
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 122, width: '100%' }}>
                <div
                  style={{
                    flex: 1,
                    height: `${(point.enrolled / max) * 100}%`,
                    minHeight: point.enrolled > 0 ? 3 : 0,
                    background: 'var(--chart-1)',
                    borderRadius: '3px 3px 0 0',
                  }}
                />
                <div
                  style={{
                    flex: 1,
                    height: `${(point.completed / max) * 100}%`,
                    minHeight: point.completed > 0 ? 3 : 0,
                    background: 'var(--success)',
                    borderRadius: '3px 3px 0 0',
                  }}
                />
              </div>
              <span style={{ fontSize: 'var(--text-2xs)', color: 'var(--muted-foreground)' }}>
                {new Date(point.week).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
