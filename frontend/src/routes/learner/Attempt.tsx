import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Textarea } from '@ds/components/forms/Textarea';
import { Progress } from '@ds/components/core/Progress';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError, api } from '@/lib/api';
import { formatSeconds } from '@/lib/format';
import type { AttemptResult, AttemptView } from '@/lib/types';
import { FullPageSpinner } from '@/components/states';
import { IconCheck, IconClock, IconClose } from '@/components/icons';

type Answers = Record<string, { selectedOptions: string[]; textAnswer?: string }>;

export default function Attempt() {
  const { assessmentId } = useParams<{ assessmentId: string }>();
  const navigate = useNavigate();

  const [answers, setAnswers] = useState<Answers>({});
  const [result, setResult] = useState<AttemptResult | null>(null);
  const [startError, setStartError] = useState<ApiError | null>(null);
  const startedRef = useRef(false);

  // Starting an attempt is a write, so it must happen exactly once — React 18's
  // double-invoked effects in development would otherwise burn a second attempt.
  const [attempt, setAttempt] = useState<AttemptView | null>(null);

  const start = useMutation({
    mutationFn: () => api.post<AttemptView>(`/assessments/${assessmentId}/attempts`),
    onSuccess: (view) => {
      setAttempt(view);
      const restored: Answers = {};
      view.savedAnswers.forEach((saved) => {
        restored[saved.questionId] = {
          selectedOptions: saved.selectedOptions,
          textAnswer: saved.textAnswer ?? undefined,
        };
      });
      setAnswers(restored);
    },
    onError: (error) => {
      setStartError(error instanceof ApiError ? error : null);
    },
  });

  useEffect(() => {
    if (startedRef.current || !assessmentId) return;
    startedRef.current = true;
    start.mutate();
  }, [assessmentId, start]);

  const submit = useMutation({
    mutationFn: () =>
      api.post<AttemptResult>(`/assessments/attempts/${attempt!.attemptId}/submit`, {
        answers: Object.entries(answers).map(([questionId, value]) => ({
          questionId,
          selectedOptions: value.selectedOptions,
          textAnswer: value.textAnswer,
        })),
      }),
    onSuccess: setResult,
  });

  const remaining = useCountdown(attempt?.expiresAt);

  // Time is up: submit whatever has been answered rather than losing the attempt.
  useEffect(() => {
    if (remaining === 0 && attempt && !result && !submit.isPending) {
      submit.mutate();
    }
  }, [remaining, attempt, result, submit]);

  const answeredCount = useMemo(
    () =>
      Object.values(answers).filter(
        (value) => value.selectedOptions.length > 0 || (value.textAnswer ?? '').trim().length > 0,
      ).length,
    [answers],
  );

  if (startError) {
    return (
      <div style={{ padding: 32, maxWidth: 560, margin: '0 auto' }}>
        <Alert
          tone={startError.code === 'no_attempts_left' ? 'warning' : 'critical'}
          title={
            startError.code === 'no_attempts_left'
              ? 'No attempts left'
              : startError.code === 'attempt_expired'
                ? 'Your previous attempt expired'
                : 'Cannot start this quiz'
          }
        >
          {startError.message}
        </Alert>
        <div style={{ marginTop: 16 }}>
          <Button variant="outline" onClick={() => navigate(-1)}>
            Go back
          </Button>
        </div>
      </div>
    );
  }

  if (!attempt) return <FullPageSpinner />;
  if (result) return <ResultView result={result} onDone={() => navigate(-1)} />;

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--background)' }}>
      <header
        className="row row-gap-3"
        style={{
          padding: '12px 18px',
          borderBottom: '1px solid var(--border)',
          background: 'var(--card)',
          position: 'sticky',
          top: 0,
          zIndex: 100,
        }}
      >
        <button type="button" className="icon-button" onClick={() => navigate(-1)} aria-label="Leave the quiz">
          <IconClose />
        </button>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div className="truncate" style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}>
            {attempt.title}
          </div>
          <div className="meta" style={{ marginTop: 2 }}>
            <span>
              Attempt {attempt.attemptNumber}
              {attempt.maxAttempts > 0 ? ` of ${attempt.maxAttempts}` : ''}
            </span>
            <span className="meta-dot" />
            <span>Pass mark {attempt.passingScore}%</span>
          </div>
        </div>

        {remaining !== null && (
          <Badge tone={remaining < 120 ? 'danger' : remaining < 300 ? 'warning' : 'neutral'} size="md">
            <IconClock size={13} />
            {formatSeconds(remaining)}
          </Badge>
        )}
      </header>

      <div style={{ maxWidth: 760, margin: '0 auto', padding: '26px 20px 120px' }}>
        {attempt.description && (
          <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 20 }}>
            {attempt.description}
          </p>
        )}

        <div className="stack stack-4">
          {attempt.questions.map((question, index) => {
            const value = answers[question.id] ?? { selectedOptions: [] };
            const multi = question.type === 'MULTI_CHOICE';

            return (
              <section key={question.id} className="surface" style={{ padding: 20 }}>
                <div className="row row-gap-2" style={{ marginBottom: 10, alignItems: 'baseline' }}>
                  <span className="ln-eyebrow">Question {index + 1}</span>
                  <div className="spacer" />
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                    {question.points} {question.points === 1 ? 'point' : 'points'}
                  </span>
                </div>

                <h2 style={{ fontSize: 'var(--text-base)', lineHeight: 1.45, marginBottom: 14 }}>
                  {question.prompt}
                </h2>

                {multi && (
                  <p style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)', marginBottom: 10 }}>
                    Select all that apply.
                  </p>
                )}

                {question.options.length > 0 ? (
                  <div className="stack stack-2">
                    {question.options.map((option) => {
                      const selected = value.selectedOptions.includes(option.id);
                      return (
                        <label
                          key={option.id}
                          style={{
                            display: 'flex',
                            gap: 11,
                            alignItems: 'flex-start',
                            padding: '11px 13px',
                            borderRadius: 'var(--radius-md)',
                            border: `1px solid ${selected ? 'var(--primary)' : 'var(--input)'}`,
                            background: selected ? 'var(--primary-soft)' : 'var(--card)',
                            cursor: 'pointer',
                            minHeight: 'var(--tap-min)',
                          }}
                        >
                          <input
                            type={multi ? 'checkbox' : 'radio'}
                            name={question.id}
                            checked={selected}
                            onChange={() =>
                              setAnswers((current) => ({
                                ...current,
                                [question.id]: {
                                  selectedOptions: multi
                                    ? selected
                                      ? value.selectedOptions.filter((id) => id !== option.id)
                                      : [...value.selectedOptions, option.id]
                                    : [option.id],
                                },
                              }))
                            }
                            style={{ marginTop: 2, accentColor: 'var(--primary)' }}
                          />
                          <span style={{ fontSize: 'var(--text-sm)' }}>{option.label}</span>
                        </label>
                      );
                    })}
                  </div>
                ) : (
                  <Textarea
                    rows={question.type === 'ESSAY' ? 7 : 2}
                    value={value.textAnswer ?? ''}
                    onChange={(event) =>
                      setAnswers((current) => ({
                        ...current,
                        [question.id]: { selectedOptions: [], textAnswer: event.target.value },
                      }))
                    }
                    placeholder={
                      question.type === 'ESSAY'
                        ? 'Write your answer. An instructor will grade this one.'
                        : 'Your answer'
                    }
                    aria-label={`Answer for question ${index + 1}`}
                  />
                )}
              </section>
            );
          })}
        </div>
      </div>

      <footer
        style={{
          position: 'fixed',
          insetInline: 0,
          bottom: 0,
          background: 'var(--card)',
          borderTop: '1px solid var(--border)',
          padding: '12px 20px',
          boxShadow: 'var(--shadow-md)',
        }}
      >
        <div className="row row-gap-4" style={{ maxWidth: 760, margin: '0 auto' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)', marginBottom: 5 }}>
              {answeredCount} of {attempt.questions.length} answered
            </div>
            <Progress value={(answeredCount / attempt.questions.length) * 100} size="xs" />
          </div>
          <Button size="lg" onClick={() => submit.mutate()} loading={submit.isPending}>
            Submit answers
          </Button>
        </div>
      </footer>
    </div>
  );
}

function ResultView({ result, onDone }: { result: AttemptResult; onDone: () => void }) {
  const pending = result.requiresGrading;

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--background)', padding: '40px 20px 80px' }}>
      <div style={{ maxWidth: 720, margin: '0 auto' }}>
        <div
          className="ln-panel"
          style={{ textAlign: 'center', padding: 'var(--space-8)' }}
        >
          <div
            style={{
              width: 58,
              height: 58,
              borderRadius: '50%',
              margin: '0 auto 16px',
              display: 'grid',
              placeItems: 'center',
              background: pending
                ? 'var(--warning-soft)'
                : result.passed
                  ? 'var(--success-soft)'
                  : 'var(--danger-soft)',
              color: pending ? 'var(--warning)' : result.passed ? 'var(--success)' : 'var(--destructive)',
            }}
          >
            {pending ? <IconClock size={24} /> : result.passed ? <IconCheck size={24} /> : <IconClose size={24} />}
          </div>

          <h1 style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontWeight: 400, fontSize: 'var(--text-3xl)' }}>
            {pending ? 'Submitted' : result.passed ? 'Passed' : 'Not passed'}
          </h1>

          <p style={{ marginTop: 10, fontSize: 'var(--text-sm)', opacity: 0.85 }}>
            {pending
              ? 'Your written answers are with an instructor. We will let you know once they are graded.'
              : `You scored ${result.percentage}% — the pass mark is ${result.passingScore}%.`}
          </p>

          {!pending && (
            <div style={{ maxWidth: 320, margin: '20px auto 0' }}>
              <Progress value={Number(result.percentage)} size="md" showLabel />
            </div>
          )}

          <div className="row row-gap-3" style={{ justifyContent: 'center', marginTop: 24 }}>
            <Button onClick={onDone}>Back to the course</Button>
            {!result.passed && !pending && result.attemptsRemaining > 0 && (
              <Button variant="outline" onClick={() => window.location.reload()}>
                Try again ({result.attemptsRemaining} left)
              </Button>
            )}
          </div>
        </div>

        {result.review.length > 0 && (
          <section style={{ marginTop: 26 }}>
            <h2 style={{ fontSize: 'var(--text-lg)', marginBottom: 14 }}>Your answers</h2>
            <div className="stack stack-3">
              {result.review.map((item, index) => (
                <article key={item.questionId} className="surface" style={{ padding: 18 }}>
                  <div className="row row-gap-2" style={{ marginBottom: 8, alignItems: 'baseline' }}>
                    <span className="ln-eyebrow">Question {index + 1}</span>
                    <div className="spacer" />
                    {item.correct === null || item.correct === undefined ? (
                      <Badge tone="warning" size="sm">
                        Awaiting grading
                      </Badge>
                    ) : item.correct ? (
                      <Badge tone="success" size="sm" dot>
                        Correct
                      </Badge>
                    ) : (
                      <Badge tone="danger" size="sm" dot>
                        Incorrect
                      </Badge>
                    )}
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                      {item.pointsAwarded}/{item.points}
                    </span>
                  </div>

                  <p style={{ fontSize: 'var(--text-sm)', lineHeight: 1.5, marginBottom: 10 }}>{item.prompt}</p>

                  {item.options.length > 0 ? (
                    <div className="stack stack-2">
                      {item.options.map((option) => {
                        const chosen = item.selectedOptions.includes(option.id);
                        const isCorrect = item.correctOptions.includes(option.id);
                        return (
                          <div
                            key={option.id}
                            style={{
                              padding: '8px 11px',
                              borderRadius: 'var(--radius-sm)',
                              fontSize: 'var(--text-sm)',
                              border: '1px solid',
                              borderColor: isCorrect
                                ? 'var(--success)'
                                : chosen
                                  ? 'var(--destructive)'
                                  : 'var(--border)',
                              background: isCorrect
                                ? 'var(--success-soft)'
                                : chosen
                                  ? 'var(--danger-soft)'
                                  : 'transparent',
                            }}
                          >
                            {option.label}
                            {chosen && (
                              <span style={{ fontSize: 'var(--text-2xs)', marginInlineStart: 8, opacity: 0.75 }}>
                                your answer
                              </span>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ) : item.textAnswer ? (
                    <blockquote
                      style={{
                        padding: '10px 13px',
                        borderInlineStart: '3px solid var(--border-strong)',
                        fontSize: 'var(--text-sm)',
                        color: 'var(--muted-foreground)',
                      }}
                    >
                      {item.textAnswer}
                    </blockquote>
                  ) : null}

                  {item.explanation && (
                    <p
                      style={{
                        marginTop: 10,
                        fontSize: 'var(--text-xs)',
                        color: 'var(--muted-foreground)',
                        lineHeight: 1.6,
                      }}
                    >
                      {item.explanation}
                    </p>
                  )}

                  {item.feedback && (
                    <div style={{ marginTop: 10 }}>
                      <Alert tone="info" title="Instructor feedback">
                        {item.feedback}
                      </Alert>
                    </div>
                  )}
                </article>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

/** Seconds left until `expiresAt`, or null when the assessment is untimed. */
function useCountdown(expiresAt?: string | null): number | null {
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    if (!expiresAt) {
      setRemaining(null);
      return;
    }
    const target = new Date(expiresAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.floor((target - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);

  return remaining;
}
