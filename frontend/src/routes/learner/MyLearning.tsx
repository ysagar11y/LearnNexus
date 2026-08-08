import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Tabs } from '@ds/components/navigation/Tabs';
import { Progress } from '@ds/components/core/Progress';
import { Badge } from '@ds/components/feedback/Badge';
import { Button } from '@ds/components/forms/Button';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api } from '@/lib/api';
import { dueLabel, formatDate, formatDuration } from '@/lib/format';
import type { EnrollmentSummary } from '@/lib/types';
import { ErrorState, LoadingRows, PageHeader } from '@/components/states';
import { IconSearch } from '@/components/icons';
import { CourseThumb } from './Dashboard';

type Filter = 'all' | 'in-progress' | 'not-started' | 'completed' | 'overdue';

export default function MyLearning() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<Filter>('all');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['my-learning'],
    queryFn: () => api.get<EnrollmentSummary[]>('/my/learning'),
  });

  const buckets = useMemo(() => {
    const all = data ?? [];
    return {
      all,
      'in-progress': all.filter((e) => e.status === 'ACTIVE' && e.progressPercent > 0),
      'not-started': all.filter((e) => e.status === 'ACTIVE' && e.progressPercent === 0),
      completed: all.filter((e) => e.status === 'COMPLETED'),
      overdue: all.filter((e) => e.overdue),
    } satisfies Record<Filter, EnrollmentSummary[]>;
  }, [data]);

  const visible = buckets[filter];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner">
      <PageHeader
        title="My learning"
        subtitle="Everything assigned to you, and everything you have joined."
      />

      <div style={{ marginBottom: 18 }}>
        <Tabs
          value={filter}
          onValueChange={(value) => setFilter(value as Filter)}
          tabs={[
            { value: 'all', label: 'All', count: buckets.all.length },
            { value: 'in-progress', label: 'In progress', count: buckets['in-progress'].length },
            { value: 'not-started', label: 'Not started', count: buckets['not-started'].length },
            { value: 'completed', label: 'Completed', count: buckets.completed.length },
            { value: 'overdue', label: 'Overdue', count: buckets.overdue.length },
          ]}
        />
      </div>

      {isLoading ? (
        <LoadingRows rows={5} />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<IconSearch size={24} />}
          title={filter === 'all' ? 'Nothing assigned yet' : 'Nothing here'}
          description={
            filter === 'all'
              ? 'When someone assigns you a course it will appear here. In the meantime, the catalog is open.'
              : 'Try a different filter, or browse the catalog for something new.'
          }
          action={
            <Button onClick={() => navigate('/catalog')}>Browse the catalog</Button>
          }
        />
      ) : (
        <div className="surface">
          {visible.map((enrollment) => {
            const due = dueLabel(enrollment.dueAt);
            const done = enrollment.status === 'COMPLETED';

            return (
              <div key={enrollment.id} className="surface-row">
                <CourseThumb
                  title={enrollment.courseTitle}
                  url={enrollment.thumbnailUrl}
                  width={78}
                  height={52}
                />

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="row row-gap-2" style={{ flexWrap: 'wrap' }}>
                    <span
                      className="truncate"
                      style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}
                    >
                      {enrollment.courseTitle}
                    </span>
                    {enrollment.mandatory && (
                      <Badge tone="accent" size="sm">
                        Required
                      </Badge>
                    )}
                    <Badge status={enrollment.status} size="sm" />
                  </div>

                  <div className="meta" style={{ marginTop: 4 }}>
                    {enrollment.categoryName && (
                      <>
                        <span>{enrollment.categoryName}</span>
                        <span className="meta-dot" />
                      </>
                    )}
                    <span>{formatDuration(enrollment.estimatedMinutes)}</span>
                    <span className="meta-dot" />
                    <span>
                      {enrollment.lessonsCompleted}/{enrollment.lessonCount} lessons
                    </span>
                    {done ? (
                      <>
                        <span className="meta-dot" />
                        <span>Completed {formatDate(enrollment.completedAt)}</span>
                      </>
                    ) : enrollment.dueAt ? (
                      <>
                        <span className="meta-dot" />
                        <span style={{ color: due.urgent ? 'var(--destructive)' : undefined }}>
                          {due.text}
                        </span>
                      </>
                    ) : null}
                  </div>

                  <div style={{ marginTop: 9, maxWidth: 360 }}>
                    <Progress value={enrollment.progressPercent} size="xs" />
                  </div>
                </div>

                <Button
                  size="sm"
                  variant={done ? 'outline' : 'primary'}
                  onClick={() => navigate(`/learn/${enrollment.courseId}`)}
                >
                  {done ? 'Review' : enrollment.progressPercent > 0 ? 'Resume' : 'Start'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
