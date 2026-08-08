import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Select } from '@ds/components/forms/Select';
import { Textarea } from '@ds/components/forms/Textarea';
import { Badge } from '@ds/components/feedback/Badge';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Tabs } from '@ds/components/navigation/Tabs';

import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatDuration, relativeTime } from '@/lib/format';
import type { Category, CourseDetail, CourseSummary, Page } from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconCourses, IconPlus, IconSearch } from '@/components/icons';

export default function AdminCourses() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();

  const [status, setStatus] = useState('');
  const [term, setTerm] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Category[]>('/categories'),
  });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-courses', term, status, categoryId, page],
    queryFn: () =>
      api.get<Page<CourseSummary>>('/courses', {
        query: term || undefined,
        status: status || undefined,
        categoryId: categoryId || undefined,
        page,
        size: 20,
      }),
  });

  const columns: Column<CourseSummary>[] = [
    {
      key: 'title',
      header: 'Course',
      render: (course) => (
        <StackedCell
          primary={course.title}
          secondary={[course.categoryName, course.ownerName].filter(Boolean).join(' · ') || undefined}
        />
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      render: (course) => <Badge status={course.status} size="sm" />,
    },
    {
      key: 'lessons',
      header: 'Lessons',
      numeric: true,
      width: 90,
      render: (course) => course.lessonCount,
    },
    {
      key: 'length',
      header: 'Length',
      numeric: true,
      width: 100,
      render: (course) => formatDuration(course.estimatedMinutes),
    },
    {
      key: 'enrolled',
      header: 'Enrolled',
      numeric: true,
      width: 100,
      render: (course) => course.enrolledCount,
    },
    {
      key: 'progress',
      header: 'Avg progress',
      numeric: true,
      width: 120,
      render: (course) => (course.averageProgress == null ? '—' : `${course.averageProgress}%`),
    },
    {
      key: 'updated',
      header: 'Updated',
      width: 130,
      render: (course) => (
        <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>
          {relativeTime(course.updatedAt)}
        </span>
      ),
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Courses"
        subtitle="The content library for this workspace."
        actions={
          hasRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUTHOR', 'INSTRUCTOR') ? (
            <Button onClick={() => setCreating(true)}>
              <IconPlus size={15} />
              New course
            </Button>
          ) : undefined
        }
      />

      <div style={{ marginBottom: 16 }}>
        <Tabs
          value={status}
          onValueChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
          tabs={[
            { value: '', label: 'All' },
            { value: 'PUBLISHED', label: 'Published' },
            { value: 'DRAFT', label: 'Draft' },
            { value: 'IN_REVIEW', label: 'In review' },
            { value: 'ARCHIVED', label: 'Archived' },
          ]}
        />
      </div>

      <div className="filter-bar">
        <div style={{ flex: 1, minWidth: 220, maxWidth: 360 }}>
          <Input
            value={term}
            onChange={(event) => {
              setTerm(event.target.value);
              setPage(0);
            }}
            placeholder="Search courses"
            leading={<IconSearch size={15} />}
            aria-label="Search courses"
          />
        </div>
        <div className="filter-control">
          <Select
            value={categoryId}
            onValueChange={(value) => {
              setCategoryId(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All categories' },
              ...(categories ?? []).map((category) => ({
                value: category.id,
                label: category.name,
              })),
            ]}
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(course) => course.id}
        loading={isLoading}
        onRowClick={(course) => navigate(`/admin/courses/${course.id}`)}
        empty={
          <EmptyState
            icon={<IconCourses size={24} />}
            title={term || status ? 'No courses match' : 'No courses yet'}
            description={
              term || status
                ? 'Try a different search or filter.'
                : 'Create your first course and start building the library.'
            }
            action={<Button onClick={() => setCreating(true)}>New course</Button>}
          />
        }
      />

      {data && (
        <Pager
          page={data.page}
          size={data.size}
          totalItems={data.totalItems}
          totalPages={data.totalPages}
          onChange={setPage}
        />
      )}

      <CreateCourseDialog
        open={creating}
        categories={categories ?? []}
        onClose={() => setCreating(false)}
        onCreated={(course) => {
          queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
          navigate(`/admin/courses/${course.summary.id}`);
        }}
      />
    </div>
  );
}

function CreateCourseDialog({
  open,
  categories,
  onClose,
  onCreated,
}: {
  open: boolean;
  categories: Category[];
  onClose: () => void;
  onCreated: (course: CourseDetail) => void;
}) {
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [level, setLevel] = useState('BEGINNER');
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api.post<CourseDetail>('/courses', {
        title: title.trim(),
        summary: summary.trim() || null,
        categoryId: categoryId || null,
        level,
        deliveryType: 'SELF_PACED',
        enrollmentMode: 'MANUAL',
        language: 'en',
        estimatedMinutes: 0,
        passingScore: 70,
        mandatory: false,
        certificateEnabled: true,
        tags: [],
        prerequisiteIds: [],
        instructorIds: [],
      }),
    onSuccess: (course) => {
      setTitle('');
      setSummary('');
      onClose();
      onCreated(course);
    },
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not create the course.'),
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="New course"
      description="You can fill in the rest once it exists."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={create.isPending}
            disabled={!title.trim()}
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            Create and edit
          </Button>
        </>
      }
    >
      {error && (
        <p style={{ color: 'var(--destructive)', fontSize: 'var(--text-sm)', marginBottom: 12 }}>
          {error}
        </p>
      )}

      <div className="field">
        <Label htmlFor="course-title" required>
          Title
        </Label>
        <Input
          id="course-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Designing Distributed Systems"
          autoFocus
        />
      </div>

      <div className="field">
        <Label htmlFor="course-summary" hint="Shown on catalog cards">
          Summary
        </Label>
        <Textarea
          id="course-summary"
          rows={3}
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          maxLength={600}
          placeholder="One or two sentences on what a learner will be able to do afterwards."
        />
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="course-category">Category</Label>
          <Select
            id="course-category"
            value={categoryId}
            onValueChange={setCategoryId}
            options={[
              { value: '', label: 'Uncategorised' },
              ...categories.map((category) => ({ value: category.id, label: category.name })),
            ]}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="course-level">Level</Label>
          <Select
            id="course-level"
            value={level}
            onValueChange={setLevel}
            options={[
              { value: 'BEGINNER', label: 'Beginner' },
              { value: 'INTERMEDIATE', label: 'Intermediate' },
              { value: 'ADVANCED', label: 'Advanced' },
            ]}
          />
        </div>
      </div>
    </Dialog>
  );
}
