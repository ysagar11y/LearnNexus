import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Textarea } from '@ds/components/forms/Textarea';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Dialog } from '@ds/components/overlays/Dialog';

import { api } from '@/lib/api';
import { relativeTime } from '@/lib/format';
import type { AttemptResult, GradingQueueItem, Page } from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconCheck, IconGrading } from '@/components/icons';

export default function AdminGrading() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [attemptId, setAttemptId] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['grading-queue', page],
    queryFn: () => api.get<Page<GradingQueueItem>>('/assessments/grading-queue', { page, size: 20 }),
  });

  const columns: Column<GradingQueueItem>[] = [
    {
      key: 'learner',
      header: 'Learner',
      render: (row) => <StackedCell primary={row.learnerName} secondary={row.courseTitle} />,
    },
    {
      key: 'assessment',
      header: 'Assessment',
      render: (row) => row.assessmentTitle ?? '—',
    },
    {
      key: 'pending',
      header: 'To grade',
      numeric: true,
      width: 100,
      render: (row) => row.pendingQuestions,
    },
    {
      key: 'submitted',
      header: 'Submitted',
      width: 150,
      render: (row) => (
        <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>
          {relativeTime(row.submittedAt)}
        </span>
      ),
    },
    {
      key: 'action',
      header: '',
      width: 90,
      render: () => <Button size="sm">Grade</Button>,
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Grading queue"
        subtitle="Written answers waiting on a human. Learners cannot finish these courses until they are graded."
      />

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(row) => row.attemptId}
        loading={isLoading}
        onRowClick={(row) => setAttemptId(row.attemptId)}
        empty={
          <EmptyState
            icon={<IconCheck size={24} />}
            title="Nothing to grade"
            description="Every submission has been graded. Auto-graded questions never appear here."
            action={<Button variant="outline" onClick={() => refetch()}>Refresh</Button>}
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

      <GradeDialog
        attemptId={attemptId}
        onClose={() => setAttemptId(null)}
        onGraded={() => {
          queryClient.invalidateQueries({ queryKey: ['grading-queue'] });
          queryClient.invalidateQueries({ queryKey: ['grading-count'] });
          setAttemptId(null);
        }}
      />
    </div>
  );
}

function GradeDialog({
  attemptId,
  onClose,
  onGraded,
}: {
  attemptId: string | null;
  onClose: () => void;
  onGraded: () => void;
}) {
  const [grades, setGrades] = useState<Record<string, { points: number; feedback: string }>>({});

  const { data } = useQuery({
    queryKey: ['attempt', attemptId],
    queryFn: () => api.get<AttemptResult>(`/assessments/attempts/${attemptId}/result`),
    enabled: !!attemptId,
  });

  useEffect(() => {
    if (!data) return;
    const initial: Record<string, { points: number; feedback: string }> = {};
    data.review
      .filter((item) => item.correct === null || item.correct === undefined)
      .forEach((item) => {
        initial[item.questionId] = { points: 0, feedback: '' };
      });
    setGrades(initial);
  }, [data]);

  const submit = useMutation({
    mutationFn: () =>
      api.post(`/assessments/attempts/${attemptId}/grade`, {
        grades: Object.entries(grades).map(([questionId, value]) => ({
          questionId,
          pointsAwarded: value.points,
          feedback: value.feedback || null,
        })),
      }),
    onSuccess: onGraded,
  });

  const pending = (data?.review ?? []).filter(
    (item) => item.correct === null || item.correct === undefined,
  );

  return (
    <Dialog
      open={!!attemptId}
      onClose={onClose}
      title="Grade submission"
      description={data ? `${data.title} · attempt ${data.attemptNumber}` : undefined}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={submit.isPending} onClick={() => submit.mutate()}>
            <IconGrading size={15} />
            Save grades
          </Button>
        </>
      }
    >
      {!data ? null : (
        <div className="stack stack-4">
          <Alert tone="info" title="Auto-graded questions are already scored">
            This learner has {data.score} of {data.maxScore} points so far. The pass mark is{' '}
            {data.passingScore}%.
          </Alert>

          {pending.map((item) => (
            <section key={item.questionId} className="surface" style={{ padding: 16, boxShadow: 'none' }}>
              <div className="row row-gap-2" style={{ marginBottom: 8, alignItems: 'baseline' }}>
                <Badge tone="warning" size="sm">
                  Needs grading
                </Badge>
                <div className="spacer" />
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                  Worth {item.points} points
                </span>
              </div>

              <p style={{ fontSize: 'var(--text-sm)', fontWeight: 500, marginBottom: 10 }}>
                {item.prompt}
              </p>

              <blockquote
                style={{
                  padding: '12px 14px',
                  background: 'var(--muted)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 'var(--text-sm)',
                  lineHeight: 1.6,
                  marginBottom: 14,
                  whiteSpace: 'pre-wrap',
                }}
              >
                {item.textAnswer || <span className="muted">No answer given.</span>}
              </blockquote>

              <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
                <div className="field" style={{ width: 130, margin: 0 }}>
                  <Label htmlFor={`points-${item.questionId}`}>Points</Label>
                  <Input
                    id={`points-${item.questionId}`}
                    type="number"
                    min={0}
                    max={item.points}
                    step="0.5"
                    value={grades[item.questionId]?.points ?? 0}
                    onChange={(event) =>
                      setGrades((current) => ({
                        ...current,
                        [item.questionId]: {
                          points: Number(event.target.value),
                          feedback: current[item.questionId]?.feedback ?? '',
                        },
                      }))
                    }
                  />
                </div>
                <div className="field" style={{ flex: 1, margin: 0 }}>
                  <Label htmlFor={`feedback-${item.questionId}`} hint="Shown to the learner">
                    Feedback
                  </Label>
                  <Textarea
                    id={`feedback-${item.questionId}`}
                    rows={3}
                    value={grades[item.questionId]?.feedback ?? ''}
                    onChange={(event) =>
                      setGrades((current) => ({
                        ...current,
                        [item.questionId]: {
                          points: current[item.questionId]?.points ?? 0,
                          feedback: event.target.value,
                        },
                      }))
                    }
                    placeholder="What was strong, and what would make it better."
                  />
                </div>
              </div>
            </section>
          ))}
        </div>
      )}
    </Dialog>
  );
}
