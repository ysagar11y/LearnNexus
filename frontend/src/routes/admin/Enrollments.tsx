import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Select } from '@ds/components/forms/Select';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Switch } from '@ds/components/forms/Switch';
import { Checkbox } from '@ds/components/forms/Checkbox';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Progress } from '@ds/components/core/Progress';

import { ApiError, api } from '@/lib/api';
import { dueLabel, formatDate } from '@/lib/format';
import type {
  CourseSummary,
  EnrollmentSummary,
  OrgUnitNode,
  Page,
  UserSummary,
} from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconEnrollment, IconPlus } from '@/components/icons';

export default function AdminEnrollments() {
  const queryClient = useQueryClient();
  const [courseId, setCourseId] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [assigning, setAssigning] = useState(false);

  const { data: courses } = useQuery({
    queryKey: ['enrollment-courses'],
    queryFn: () => api.get<Page<CourseSummary>>('/courses', { status: 'PUBLISHED', size: 100 }),
  });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['enrollments', courseId, status, page],
    queryFn: () =>
      api.get<Page<EnrollmentSummary>>('/enrollments', {
        courseId: courseId || undefined,
        status: status || undefined,
        page,
        size: 25,
      }),
  });

  const withdraw = useMutation({
    mutationFn: (enrollmentId: string) => api.delete(`/enrollments/${enrollmentId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['enrollments'] }),
  });

  const columns: Column<EnrollmentSummary>[] = [
    {
      key: 'learner',
      header: 'Learner',
      render: (row) => <StackedCell primary={row.learnerName ?? '—'} secondary={row.learnerEmail} />,
    },
    {
      key: 'course',
      header: 'Course',
      render: (row) => <StackedCell primary={row.courseTitle} secondary={row.categoryName} />,
    },
    {
      key: 'progress',
      header: 'Progress',
      width: 150,
      render: (row) => (
        <div style={{ minWidth: 110 }}>
          <Progress value={row.progressPercent} size="xs" />
          <div className="cell-secondary">
            {row.lessonsCompleted}/{row.lessonCount} lessons
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      render: (row) => <Badge status={row.status} size="sm" />,
    },
    {
      key: 'due',
      header: 'Due',
      width: 140,
      render: (row) => {
        if (!row.dueAt) return <span className="muted">—</span>;
        const due = dueLabel(row.dueAt);
        return (
          <span style={{ fontSize: 'var(--text-xs)', color: due.overdue ? 'var(--destructive)' : undefined }}>
            {formatDate(row.dueAt)}
          </span>
        );
      },
    },
    {
      key: 'actions',
      header: '',
      width: 100,
      render: (row) =>
        row.status === 'ACTIVE' ? (
          <Button
            size="sm"
            variant="ghost"
            onClick={(event) => {
              event.stopPropagation();
              withdraw.mutate(row.id);
            }}
          >
            Withdraw
          </Button>
        ) : null,
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Enrolments"
        subtitle="Who is on which course, and how far they have got."
        actions={
          <Button onClick={() => setAssigning(true)}>
            <IconPlus size={15} />
            Assign a course
          </Button>
        }
      />

      <div className="filter-bar">
        <div style={{ minWidth: 240 }}>
          <Select
            value={courseId}
            onValueChange={(value) => {
              setCourseId(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All courses' },
              ...(courses?.items ?? []).map((course) => ({ value: course.id, label: course.title })),
            ]}
            aria-label="Course"
          />
        </div>
        <div className="filter-control">
          <Select
            value={status}
            onValueChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'Any status' },
              { value: 'ACTIVE', label: 'Active' },
              { value: 'COMPLETED', label: 'Completed' },
              { value: 'EXPIRED', label: 'Expired' },
              { value: 'WITHDRAWN', label: 'Withdrawn' },
            ]}
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(row) => row.id}
        loading={isLoading}
        empty={
          <EmptyState
            icon={<IconEnrollment size={24} />}
            title="No enrolments"
            description="Assign a course to a person or a whole department to get started."
            action={<Button onClick={() => setAssigning(true)}>Assign a course</Button>}
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

      <AssignDialog
        open={assigning}
        courses={courses?.items ?? []}
        onClose={() => setAssigning(false)}
        onAssigned={() => queryClient.invalidateQueries({ queryKey: ['enrollments'] })}
      />
    </div>
  );
}

function AssignDialog({
  open,
  courses,
  onClose,
  onAssigned,
}: {
  open: boolean;
  courses: CourseSummary[];
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [mode, setMode] = useState<'people' | 'department'>('people');
  const [courseId, setCourseId] = useState('');
  const [userIds, setUserIds] = useState<string[]>([]);
  const [orgUnitId, setOrgUnitId] = useState('');
  const [includeSubtree, setIncludeSubtree] = useState(true);
  const [dueAt, setDueAt] = useState('');
  const [notify, setNotify] = useState(true);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: people } = useQuery({
    queryKey: ['assign-people'],
    queryFn: () => api.get<Page<UserSummary>>('/users', { size: 200, status: 'ACTIVE' }),
    enabled: open && mode === 'people',
  });

  const { data: orgUnits } = useQuery({
    queryKey: ['org-units-flat'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/flat'),
    enabled: open,
  });

  const assign = useMutation({
    mutationFn: () => {
      const due = dueAt ? new Date(`${dueAt}T23:59:59Z`).toISOString() : null;
      return mode === 'people'
        ? api.post<{ enrolled: number; alreadyEnrolled: number; skipped: number }>('/enrollments', {
            courseId,
            userIds,
            dueAt: due,
            source: 'MANUAL',
            notifyLearners: notify,
          })
        : api.post<{ enrolled: number; alreadyEnrolled: number; skipped: number }>(
            '/enrollments/by-org-unit',
            { courseId, orgUnitId, includeSubtree, dueAt: due, notifyLearners: notify },
          );
    },
    onSuccess: (outcome) => {
      setResult(
        `${outcome.enrolled} enrolled` +
          (outcome.alreadyEnrolled ? `, ${outcome.alreadyEnrolled} already on it` : '') +
          (outcome.skipped ? `, ${outcome.skipped} skipped` : ''),
      );
      setUserIds([]);
      onAssigned();
    },
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not assign the course.'),
  });

  const ready = !!courseId && (mode === 'people' ? userIds.length > 0 : !!orgUnitId);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Assign a course"
      description="Pick a course, then who should take it."
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
          <Button
            loading={assign.isPending}
            disabled={!ready}
            onClick={() => {
              setError(null);
              setResult(null);
              assign.mutate();
            }}
          >
            Assign
          </Button>
        </>
      }
    >
      {error && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="critical" title="Could not assign">
            {error}
          </Alert>
        </div>
      )}
      {result && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="success" title="Assigned" onDismiss={() => setResult(null)}>
            {result}
          </Alert>
        </div>
      )}

      <div className="field">
        <Label htmlFor="assign-course" required>
          Course
        </Label>
        <Select
          id="assign-course"
          value={courseId}
          onValueChange={setCourseId}
          placeholder="Choose a published course"
          options={courses.map((course) => ({ value: course.id, label: course.title }))}
        />
      </div>

      <div className="row row-gap-2" style={{ marginBottom: 14 }}>
        <Button
          size="sm"
          variant={mode === 'people' ? 'primary' : 'outline'}
          onClick={() => setMode('people')}
        >
          Specific people
        </Button>
        <Button
          size="sm"
          variant={mode === 'department' ? 'primary' : 'outline'}
          onClick={() => setMode('department')}
        >
          A whole department
        </Button>
      </div>

      {mode === 'people' ? (
        <div className="field">
          <Label>
            People {userIds.length > 0 ? `(${userIds.length} selected)` : ''}
          </Label>
          <div
            className="surface"
            style={{ maxHeight: 240, overflowY: 'auto', boxShadow: 'none', padding: 10 }}
          >
            {(people?.items ?? []).map((person) => (
              <div key={person.id} style={{ padding: '4px 0' }}>
                <Checkbox
                  checked={userIds.includes(person.id)}
                  onCheckedChange={(checked) =>
                    setUserIds((current) =>
                      checked ? [...current, person.id] : current.filter((id) => id !== person.id),
                    )
                  }
                  label={person.displayName}
                  description={person.email}
                />
              </div>
            ))}
          </div>
        </div>
      ) : (
        <>
          <div className="field">
            <Label htmlFor="assign-org" required>
              Department
            </Label>
            <Select
              id="assign-org"
              value={orgUnitId}
              onValueChange={setOrgUnitId}
              placeholder="Choose a department"
              options={(orgUnits ?? []).map((unit) => ({
                value: unit.id,
                label: `${'— '.repeat(unit.depth)}${unit.name} (${unit.memberCount})`,
              }))}
            />
          </div>
          <div style={{ marginBottom: 14 }}>
            <Switch
              checked={includeSubtree}
              onCheckedChange={setIncludeSubtree}
              label="Include sub-departments"
              description="Everyone beneath this unit in the hierarchy."
            />
          </div>
        </>
      )}

      <div className="field">
        <Label htmlFor="assign-due" hint="Optional">
          Deadline
        </Label>
        <Input
          id="assign-due"
          type="date"
          value={dueAt}
          onChange={(event) => setDueAt(event.target.value)}
        />
      </div>

      <Switch
        checked={notify}
        onCheckedChange={setNotify}
        label="Email them"
        description="They always get an in-app notification; this adds an email."
      />
    </Dialog>
  );
}
