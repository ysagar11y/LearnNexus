import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { StatTile } from '@ds/components/core/StatTile';
import { Progress } from '@ds/components/core/Progress';
import { Badge } from '@ds/components/feedback/Badge';
import { Button } from '@ds/components/forms/Button';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { dueLabel, formatDateShort } from '@/lib/format';
import type { LearnerDashboard } from '@/lib/types';
import { ErrorState, LoadingRows, PageHeader } from '@/components/states';
import {
  IconCertificate,
  IconClock,
  IconFlame,
  IconLearning,
  IconPlay,
  IconSearch,
} from '@/components/icons';

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

export default function Dashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['learner-dashboard'],
    queryFn: () => api.get<LearnerDashboard>('/my/dashboard'),
  });

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner">
      <PageHeader
        display
        title={`${greeting()}, ${user?.firstName}.`}
        subtitle={
          data && data.overdue > 0
            ? `You have ${data.overdue} ${data.overdue === 1 ? 'course' : 'courses'} past its deadline.`
            : 'Here is where you left off.'
        }
      />

      <div className="stat-grid">
        <StatTile
          label="In progress"
          value={data?.inProgress ?? '—'}
          icon={<IconLearning size={15} />}
          caption={data ? `${data.assigned} assigned in total` : undefined}
        />
        <StatTile
          label="Completed"
          value={data?.completed ?? '—'}
          icon={<IconPlay size={15} />}
          caption={data && data.assigned > 0
            ? `${Math.round((data.completed / data.assigned) * 100)}% of your plan`
            : undefined}
        />
        <StatTile
          label="Overdue"
          value={data?.overdue ?? '—'}
          icon={<IconClock size={15} />}
          // Up is bad here, so the tone is stated rather than inferred.
          deltaTone="negative"
          caption={data?.overdue ? 'Needs attention' : 'Nothing overdue'}
        />
        <StatTile
          label="Learning time"
          value={data ? Math.round(data.learningMinutes / 60) : '—'}
          unit="h"
          icon={<IconFlame size={15} />}
          caption={
            data && data.currentStreakDays > 0
              ? `${data.currentStreakDays}-day streak`
              : 'Start a streak today'
          }
        />
      </div>

      <div className="columns section-gap">
        <section>
          <div className="surface">
            <div className="surface-header">
              <h2>Continue learning</h2>
              <Link to="/my-learning" className="link">
                See all
              </Link>
            </div>

            {isLoading ? (
              <LoadingRows rows={3} />
            ) : data && data.continueLearning.length > 0 ? (
              data.continueLearning.map((enrollment) => {
                const due = dueLabel(enrollment.dueAt);
                return (
                  <div key={enrollment.id} className="surface-row">
                    <CourseThumb title={enrollment.courseTitle} url={enrollment.thumbnailUrl} />

                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        className="truncate"
                        style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}
                      >
                        {enrollment.courseTitle}
                      </div>
                      <div className="meta" style={{ marginTop: 3 }}>
                        <span>
                          {enrollment.lessonsCompleted} of {enrollment.lessonCount} lessons
                        </span>
                        {enrollment.dueAt && (
                          <>
                            <span className="meta-dot" />
                            <span style={{ color: due.urgent ? 'var(--destructive)' : undefined }}>
                              {due.text}
                            </span>
                          </>
                        )}
                        {enrollment.mandatory && (
                          <>
                            <span className="meta-dot" />
                            <Badge tone="accent" size="sm">
                              Required
                            </Badge>
                          </>
                        )}
                      </div>
                      <div style={{ marginTop: 8, maxWidth: 320 }}>
                        <Progress value={enrollment.progressPercent} size="xs" />
                      </div>
                    </div>

                    <Button size="sm" onClick={() => navigate(`/learn/${enrollment.courseId}`)}>
                      Resume
                    </Button>
                  </div>
                );
              })
            ) : (
              <div style={{ padding: 20 }}>
                <EmptyState
                  compact
                  icon={<IconSearch size={22} />}
                  title="Nothing in progress"
                  description="Browse the catalog and start something new."
                  action={
                    <Button size="sm" onClick={() => navigate('/catalog')}>
                      Browse the catalog
                    </Button>
                  }
                />
              </div>
            )}
          </div>
        </section>

        <aside className="stack stack-4">
          <div className="surface">
            <div className="surface-header">
              <h2>Deadlines</h2>
            </div>
            {data && data.dueSoon.length > 0 ? (
              data.dueSoon.map((enrollment) => {
                const due = dueLabel(enrollment.dueAt);
                return (
                  <Link
                    key={enrollment.id}
                    to={`/learn/${enrollment.courseId}`}
                    className="surface-row"
                    style={{ textDecoration: 'none', color: 'inherit', alignItems: 'flex-start' }}
                  >
                    <div style={{ width: 40, flexShrink: 0, textAlign: 'center' }}>
                      <b
                        style={{
                          display: 'block',
                          fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
                          fontSize: 'var(--text-lg)',
                          fontWeight: 'var(--font-weight-normal)',
                          lineHeight: 1.1,
                        }}
                      >
                        {enrollment.dueAt ? new Date(enrollment.dueAt).getDate() : '—'}
                      </b>
                      <span
                        style={{
                          display: 'block',
                          fontSize: 'var(--text-2xs)',
                          textTransform: 'uppercase',
                          letterSpacing: 'var(--tracking-caps)',
                          color: 'var(--muted-foreground)',
                        }}
                      >
                        {enrollment.dueAt
                          ? new Date(enrollment.dueAt).toLocaleString('en-GB', { month: 'short' })
                          : ''}
                      </span>
                    </div>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div className="truncate" style={{ fontSize: 'var(--text-sm)', fontWeight: 500 }}>
                        {enrollment.courseTitle}
                      </div>
                      <div style={{ marginTop: 4 }}>
                        <Badge tone={due.overdue ? 'danger' : due.urgent ? 'warning' : 'neutral'} size="sm">
                          {due.text}
                        </Badge>
                      </div>
                    </div>
                  </Link>
                );
              })
            ) : (
              <div style={{ padding: '18px', fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
                No deadlines coming up.
              </div>
            )}
          </div>

          {data && data.upcomingSessions.length > 0 && (
            <div className="surface">
              <div className="surface-header">
                <h2>Live sessions</h2>
              </div>
              {data.upcomingSessions.map((session) => (
                <div key={session.id} className="surface-row" style={{ alignItems: 'flex-start' }}>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div className="truncate" style={{ fontSize: 'var(--text-sm)', fontWeight: 500 }}>
                      {session.title}
                    </div>
                    <div className="meta" style={{ marginTop: 3 }}>
                      <span>{formatDateShort(session.startsAt)}</span>
                      <span className="meta-dot" />
                      <span>{session.courseTitle}</span>
                    </div>
                  </div>
                  {session.joinUrl && (
                    <a href={session.joinUrl} target="_blank" rel="noreferrer">
                      <Button size="sm" variant="outline">
                        Join
                      </Button>
                    </a>
                  )}
                </div>
              ))}
            </div>
          )}

          {data && data.certificates > 0 && (
            <Link to="/certificates" style={{ textDecoration: 'none' }}>
              <div className="ln-panel" style={{ padding: 'var(--space-5)' }}>
                <div className="row row-gap-3">
                  <IconCertificate size={20} />
                  <div>
                    <div style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}>
                      {data.certificates} {data.certificates === 1 ? 'certificate' : 'certificates'}
                    </div>
                    <div style={{ fontSize: 'var(--text-xs)', opacity: 0.8 }}>
                      Download or share them
                    </div>
                  </div>
                </div>
              </div>
            </Link>
          )}
        </aside>
      </div>
    </div>
  );
}

/**
 * Falls back to a generated brand-tinted block keyed off the title, so a course
 * without artwork still looks deliberate rather than broken.
 */
export function CourseThumb({
  title,
  url,
  width = 64,
  height = 44,
}: {
  title: string;
  url?: string | null;
  width?: number | string;
  height?: number | string;
}) {
  if (url) {
    return (
      <img
        src={url}
        alt=""
        style={{
          width,
          height,
          objectFit: 'cover',
          borderRadius: 'var(--radius-sm)',
          flexShrink: 0,
        }}
      />
    );
  }

  const hash = Array.from(title).reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const rotation = (hash % 40) - 20;

  return (
    <div
      aria-hidden="true"
      style={{
        width,
        height,
        borderRadius: 'var(--radius-sm)',
        flexShrink: 0,
        display: 'grid',
        placeItems: 'center',
        background: `linear-gradient(140deg,
          oklch(0.91 calc(var(--brand-c) * 0.5) calc(var(--brand-h) + ${rotation})),
          oklch(0.86 calc(var(--brand-c) * 0.7) calc(var(--brand-h) + ${rotation - 16})))`,
        color: 'var(--brand-800)',
        fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
        fontSize: typeof height === 'number' && height > 60 ? 22 : 15,
      }}
    >
      {title.charAt(0).toUpperCase()}
    </div>
  );
}
