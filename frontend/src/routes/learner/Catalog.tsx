import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Input } from '@ds/components/forms/Input';
import { Select } from '@ds/components/forms/Select';
import { Button } from '@ds/components/forms/Button';
import { Badge } from '@ds/components/feedback/Badge';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError, api } from '@/lib/api';
import { formatDuration } from '@/lib/format';
import type { Category, CourseSummary, EnrollmentSummary, Page } from '@/lib/types';
import { ErrorState, LoadingCards, PageHeader } from '@/components/states';
import { IconSearch } from '@/components/icons';
import { CourseThumb } from './Dashboard';

export default function Catalog() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [term, setTerm] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [level, setLevel] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/categories'),
  });

  // Only published courses are browsable; the server enforces it too, but asking
  // for the right thing keeps the payload small.
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['catalog', term, categoryId, level],
    queryFn: () =>
      api.get<Page<CourseSummary>>('/courses', {
        query: term || undefined,
        status: 'PUBLISHED',
        categoryId: categoryId || undefined,
        level: level || undefined,
        size: 48,
      }),
  });

  const { data: myLearning } = useQuery({
    queryKey: ['my-learning'],
    queryFn: () => api.get<EnrollmentSummary[]>('/my/learning'),
  });

  const enrolledCourseIds = useMemo(
    () => new Set((myLearning ?? []).map((enrollment) => enrollment.courseId)),
    [myLearning],
  );

  const enroll = useMutation({
    mutationFn: (courseId: string) => api.post(`/my/courses/${courseId}/enroll`),
    onSuccess: (_result, courseId) => {
      queryClient.invalidateQueries({ queryKey: ['my-learning'] });
      queryClient.invalidateQueries({ queryKey: ['learner-dashboard'] });
      navigate(`/learn/${courseId}`);
    },
    onError: (caught) => {
      setNotice(caught instanceof ApiError ? caught.message : 'Could not join that course.');
    },
  });

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  const courses = data?.items ?? [];

  return (
    <div className="app-inner">
      <PageHeader
        title="Catalog"
        subtitle="Everything published in your workspace."
      />

      {notice && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="warning" title="Could not join" onDismiss={() => setNotice(null)}>
            {notice}
          </Alert>
        </div>
      )}

      <div className="filter-bar">
        <div style={{ flex: 1, minWidth: 220, maxWidth: 380 }}>
          <Input
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            placeholder="Search courses"
            leading={<IconSearch size={15} />}
            aria-label="Search courses"
          />
        </div>
        <div className="filter-control">
        <Select
          value={categoryId}
          onValueChange={setCategoryId}
          placeholder="All categories"
          options={[
            { value: '', label: 'All categories' },
            ...(categories ?? []).map((category) => ({
              value: category.id,
              label: `${category.name} (${category.courseCount})`,
            })),
          ]}
        />
        </div>
        <div className="filter-control">
        <Select
          value={level}
          onValueChange={setLevel}
          placeholder="Any level"
          options={[
            { value: '', label: 'Any level' },
            { value: 'BEGINNER', label: 'Beginner' },
            { value: 'INTERMEDIATE', label: 'Intermediate' },
            { value: 'ADVANCED', label: 'Advanced' },
          ]}
        />
        </div>
        {(term || categoryId || level) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setTerm('');
              setCategoryId('');
              setLevel('');
            }}
          >
            Clear
          </Button>
        )}
      </div>

      {isLoading ? (
        <LoadingCards count={6} />
      ) : courses.length === 0 ? (
        <EmptyState
          icon={<IconSearch size={24} />}
          title="No courses match"
          description={
            term || categoryId || level
              ? 'Try a broader search, or clear the filters.'
              : 'Nothing has been published in this workspace yet.'
          }
          action={
            <Button
              onClick={() => {
                setTerm('');
                setCategoryId('');
                setLevel('');
              }}
            >
              Clear filters
            </Button>
          }
        />
      ) : (
        <div className="card-grid">
          {courses.map((course) => {
            const enrolled = enrolledCourseIds.has(course.id);
            const selfServe = course.enrollmentMode === 'SELF';

            return (
              <article key={course.id} className="surface" style={{ display: 'flex', flexDirection: 'column' }}>
                <CourseThumb
                  title={course.title}
                  url={course.thumbnailUrl}
                  width="100%"
                  height={148}
                />

                <div className="stack stack-2" style={{ padding: 15, flex: 1 }}>
                  {course.categoryName && (
                    <span className="ln-eyebrow">{course.categoryName}</span>
                  )}
                  <h3 style={{ fontSize: 'var(--text-base)', lineHeight: 1.3 }}>{course.title}</h3>
                  {course.summary && (
                    <p
                      style={{
                        fontSize: 'var(--text-xs)',
                        color: 'var(--muted-foreground)',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                    >
                      {course.summary}
                    </p>
                  )}

                  <div className="meta" style={{ marginTop: 'auto', paddingTop: 8 }}>
                    <span>{formatDuration(course.estimatedMinutes)}</span>
                    <span className="meta-dot" />
                    <span>{course.lessonCount} lessons</span>
                    <span className="meta-dot" />
                    <span>{course.level.charAt(0) + course.level.slice(1).toLowerCase()}</span>
                  </div>

                  <div className="row row-gap-2" style={{ marginTop: 10 }}>
                    {course.mandatory && (
                      <Badge tone="accent" size="sm">
                        Required
                      </Badge>
                    )}
                    <div className="spacer" />
                    {enrolled ? (
                      <Button size="sm" variant="outline" onClick={() => navigate(`/learn/${course.id}`)}>
                        Continue
                      </Button>
                    ) : selfServe ? (
                      <Button
                        size="sm"
                        loading={enroll.isPending && enroll.variables === course.id}
                        onClick={() => enroll.mutate(course.id)}
                      >
                        Join course
                      </Button>
                    ) : (
                      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                        Assigned by admin
                      </span>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
