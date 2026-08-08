import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Progress } from '@ds/components/core/Progress';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';

import { api } from '@/lib/api';
import { formatSeconds } from '@/lib/format';
import type { PlayerLesson, PlayerView, ProgressResponse } from '@/lib/types';
import { ErrorState, FullPageSpinner } from '@/components/states';
import {
  IconCheck,
  IconChevronLeft,
  IconChevronRight,
  IconClose,
  IconGrading,
  IconPlay,
} from '@/components/icons';

/** How often playback position is sent while a timed lesson is playing. */
const HEARTBEAT_MS = 10_000;

export default function Player() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeLessonId, setActiveLessonId] = useState<string | null>(null);
  const [outlineOpen, setOutlineOpen] = useState(false);
  const [justCompleted, setJustCompleted] = useState<ProgressResponse | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['player', courseId],
    queryFn: () => api.get<PlayerView>(`/my/courses/${courseId}`),
    enabled: !!courseId,
  });

  const lessons = useMemo(
    () => (data?.modules ?? []).flatMap((module) => module.lessons),
    [data],
  );

  // Open on the lesson the learner was last on, not always the first one.
  useEffect(() => {
    if (!data || activeLessonId) return;
    setActiveLessonId(data.nextLessonId ?? lessons[0]?.id ?? null);
  }, [data, activeLessonId, lessons]);

  const active = lessons.find((lesson) => lesson.id === activeLessonId) ?? null;
  const activeIndex = lessons.findIndex((lesson) => lesson.id === activeLessonId);

  const recordProgress = useMutation({
    mutationFn: (payload: {
      lessonId: string;
      positionSeconds: number;
      watchedSeconds: number;
      completed?: boolean;
    }) => api.post<ProgressResponse>(`/my/courses/${courseId}/progress`, payload),
    onSuccess: (result, variables) => {
      if (variables.completed) {
        setJustCompleted(result);
        queryClient.invalidateQueries({ queryKey: ['player', courseId] });
        queryClient.invalidateQueries({ queryKey: ['my-learning'] });
        queryClient.invalidateQueries({ queryKey: ['learner-dashboard'] });
      }
    },
  });

  if (isLoading) return <FullPageSpinner />;
  if (error) {
    return (
      <div style={{ padding: 32, maxWidth: 620, margin: '0 auto' }}>
        <ErrorState error={error} onRetry={refetch} />
        <div style={{ marginTop: 16 }}>
          <Button variant="outline" onClick={() => navigate('/my-learning')}>
            Back to my learning
          </Button>
        </div>
      </div>
    );
  }
  if (!data) return null;

  function goTo(offset: number) {
    const next = lessons[activeIndex + offset];
    if (next) {
      setActiveLessonId(next.id);
      setJustCompleted(null);
    }
  }

  function markComplete() {
    if (!active) return;
    recordProgress.mutate({
      lessonId: active.id,
      positionSeconds: active.lastPositionSeconds,
      watchedSeconds: Math.max(active.secondsWatched, active.durationSeconds),
      completed: true,
    });
  }

  const completed = lessons.filter((lesson) => lesson.status === 'COMPLETED').length;

  return (
    <div className="player-layout">
      <div className="player-stage">
        <header
          className="row row-gap-3"
          style={{
            padding: '12px 18px',
            borderBottom: '1px solid var(--border)',
            background: 'var(--card)',
            position: 'sticky',
            top: 0,
            zIndex: 'var(--z-sticky)' as never,
          }}
        >
          <Link to="/my-learning" className="icon-button" aria-label="Back to my learning">
            <IconClose />
          </Link>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div className="truncate" style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}>
              {data.courseTitle}
            </div>
            <div className="meta" style={{ marginTop: 2 }}>
              <span>
                {completed} of {lessons.length} lessons
              </span>
              <span className="meta-dot" />
              <span>{data.progressPercent}% complete</span>
            </div>
          </div>
          <div style={{ width: 130 }} className="progress-inline">
            <Progress value={data.progressPercent} size="xs" />
          </div>
          {/* Wrapped rather than flagged directly: Button sets `display` as an
              inline style, which a stylesheet rule cannot override. */}
          <span data-mobile-only>
            <Button size="sm" variant="outline" onClick={() => setOutlineOpen((open) => !open)}>
              Lessons
            </Button>
          </span>
        </header>

        {justCompleted?.courseCompleted && (
          <div style={{ padding: '16px 18px 0' }}>
            <Alert
              tone="success"
              title="Course complete"
              action={
                justCompleted.certificateId ? (
                  <Button size="sm" onClick={() => navigate('/certificates')}>
                    View certificate
                  </Button>
                ) : undefined
              }
            >
              You have finished {data.courseTitle}.
              {justCompleted.certificateId ? ' Your certificate is ready.' : ''}
            </Alert>
          </div>
        )}

        {active ? (
          <LessonStage
            lesson={active}
            courseId={courseId!}
            onHeartbeat={(positionSeconds, watchedSeconds) =>
              recordProgress.mutate({ lessonId: active.id, positionSeconds, watchedSeconds })
            }
            onEnded={markComplete}
          />
        ) : (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--muted-foreground)' }}>
            This course has no lessons yet.
          </div>
        )}

        {active && (
          <footer
            className="row row-gap-3"
            style={{
              padding: '14px 18px 26px',
              borderTop: '1px solid var(--border)',
              marginTop: 'auto',
            }}
          >
            <Button
              variant="ghost"
              size="sm"
              disabled={activeIndex <= 0}
              onClick={() => goTo(-1)}
            >
              <IconChevronLeft size={15} />
              Previous
            </Button>

            <div className="spacer" />

            {active.contentType === 'QUIZ' && active.assessmentId ? (
              <Button
                onClick={() => navigate(`/assessments/${active.assessmentId}/attempt`)}
                variant={active.assessmentPassed ? 'outline' : 'primary'}
              >
                <IconGrading size={15} />
                {active.assessmentPassed ? 'Review quiz' : 'Take the quiz'}
              </Button>
            ) : active.status === 'COMPLETED' ? (
              <Badge status="COMPLETED" size="md" />
            ) : (
              <Button onClick={markComplete} loading={recordProgress.isPending}>
                <IconCheck size={15} />
                Mark complete
              </Button>
            )}

            <Button
              variant="ghost"
              size="sm"
              disabled={activeIndex >= lessons.length - 1}
              onClick={() => goTo(1)}
            >
              Next
              <IconChevronRight size={15} />
            </Button>
          </footer>
        )}
      </div>

      <aside
        className="player-outline"
        style={{ display: outlineOpen ? 'block' : undefined }}
        aria-label="Course outline"
      >
        <div style={{ padding: '16px 18px', borderBottom: '1px solid var(--border)' }}>
          <div className="ln-eyebrow">Course outline</div>
          <div style={{ marginTop: 10 }}>
            <Progress value={data.progressPercent} size="sm" showLabel />
          </div>
        </div>

        {data.modules.map((module) => (
          <section key={module.id}>
            <h2
              style={{
                padding: '14px 18px 6px',
                fontSize: 'var(--text-xs)',
                fontWeight: 'var(--font-weight-semibold)',
                letterSpacing: 'var(--tracking-caps)',
                textTransform: 'uppercase',
                color: 'var(--muted-foreground)',
              }}
            >
              {module.title}
            </h2>
            {module.lessons.map((lesson, index) => {
              const isActive = lesson.id === activeLessonId;
              const done = lesson.status === 'COMPLETED';

              return (
                <button
                  key={lesson.id}
                  type="button"
                  onClick={() => {
                    setActiveLessonId(lesson.id);
                    setOutlineOpen(false);
                    setJustCompleted(null);
                  }}
                  aria-current={isActive ? 'true' : undefined}
                  style={{
                    display: 'flex',
                    gap: 11,
                    width: '100%',
                    textAlign: 'start',
                    padding: '10px 18px',
                    border: 0,
                    background: isActive ? 'var(--primary-soft)' : 'transparent',
                    color: isActive ? 'var(--primary-soft-foreground)' : 'var(--foreground)',
                    cursor: 'pointer',
                    alignItems: 'flex-start',
                  }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 20,
                      height: 20,
                      borderRadius: '50%',
                      flexShrink: 0,
                      marginTop: 1,
                      display: 'grid',
                      placeItems: 'center',
                      fontSize: 10,
                      background: done ? 'var(--success)' : 'var(--muted)',
                      color: done ? 'var(--success-foreground)' : 'var(--muted-foreground)',
                    }}
                  >
                    {done ? <IconCheck size={11} /> : index + 1}
                  </span>

                  <span style={{ minWidth: 0, flex: 1 }}>
                    <span
                      style={{
                        display: 'block',
                        fontSize: 'var(--text-sm)',
                        fontWeight: isActive ? 'var(--font-weight-semibold)' : 'var(--font-weight-normal)',
                      }}
                    >
                      {lesson.title}
                    </span>
                    <span
                      style={{
                        display: 'block',
                        marginTop: 2,
                        fontSize: 'var(--text-2xs)',
                        color: 'var(--muted-foreground)',
                      }}
                    >
                      {lesson.contentType === 'QUIZ'
                        ? lesson.assessmentPassed
                          ? `Quiz · passed${lesson.assessmentScore != null ? ` (${lesson.assessmentScore}%)` : ''}`
                          : 'Quiz'
                        : `${lesson.contentType.charAt(0)}${lesson.contentType.slice(1).toLowerCase()}${
                            lesson.durationSeconds ? ` · ${formatSeconds(lesson.durationSeconds)}` : ''
                          }`}
                    </span>
                  </span>
                </button>
              );
            })}
          </section>
        ))}
      </aside>
    </div>
  );
}

/**
 * Renders one lesson.
 *
 * Video reports position on an interval rather than on every `timeupdate`
 * (which fires ~4×/second) — the server only needs enough resolution to resume
 * playback and credit watch time.
 */
function LessonStage({
  lesson,
  onHeartbeat,
  onEnded,
}: {
  lesson: PlayerLesson;
  courseId: string;
  onHeartbeat: (positionSeconds: number, watchedSeconds: number) => void;
  onEnded: () => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const watchedRef = useRef(lesson.secondsWatched);

  useEffect(() => {
    watchedRef.current = lesson.secondsWatched;
  }, [lesson.id, lesson.secondsWatched]);

  useEffect(() => {
    const element = videoRef.current;
    if (!element) return;

    // Resume where they stopped, but not right at the end.
    if (lesson.lastPositionSeconds > 0 && lesson.lastPositionSeconds < lesson.durationSeconds - 5) {
      element.currentTime = lesson.lastPositionSeconds;
    }

    const timer = setInterval(() => {
      if (element.paused) return;
      watchedRef.current += HEARTBEAT_MS / 1000;
      onHeartbeat(Math.floor(element.currentTime), Math.floor(watchedRef.current));
    }, HEARTBEAT_MS);

    return () => clearInterval(timer);
  }, [lesson.id, lesson.lastPositionSeconds, lesson.durationSeconds, onHeartbeat]);

  if (lesson.contentType === 'VIDEO' || lesson.contentType === 'AUDIO') {
    return (
      <div className="player-media">
        {lesson.contentUrl ? (
          <video ref={videoRef} controls playsInline onEnded={onEnded} src={lesson.contentUrl} />
        ) : (
          <div className="stack stack-3" style={{ alignItems: 'center', padding: 40 }}>
            <IconPlay size={28} />
            <p style={{ fontSize: 'var(--text-sm)' }}>
              No media has been attached to this lesson yet.
            </p>
          </div>
        )}
      </div>
    );
  }

  if (lesson.contentType === 'PDF' && lesson.contentUrl) {
    return (
      <div style={{ height: '68vh', background: 'var(--muted)' }}>
        <iframe title={lesson.title} src={lesson.contentUrl} style={{ width: '100%', height: '100%', border: 0 }} />
      </div>
    );
  }

  if (lesson.contentType === 'LINK' && lesson.contentUrl) {
    return (
      <div style={{ padding: '40px 22px' }}>
        <div className="ln-panel" style={{ maxWidth: 560 }}>
          <h2 style={{ fontSize: 'var(--text-lg)' }}>{lesson.title}</h2>
          <p style={{ marginTop: 8, fontSize: 'var(--text-sm)', opacity: 0.85 }}>
            This lesson lives on another site.
          </p>
          <a href={lesson.contentUrl} target="_blank" rel="noreferrer" style={{ display: 'inline-block', marginTop: 14 }}>
            <Button>Open the material</Button>
          </a>
        </div>
      </div>
    );
  }

  if (lesson.contentType === 'QUIZ') {
    return (
      <div style={{ padding: '48px 22px', display: 'grid', placeItems: 'center' }}>
        <div className="ln-panel" style={{ maxWidth: 520, textAlign: 'center' }}>
          <IconGrading size={26} style={{ margin: '0 auto 12px' }} />
          <h2 style={{ fontSize: 'var(--text-lg)' }}>{lesson.title}</h2>
          <p style={{ marginTop: 8, fontSize: 'var(--text-sm)', opacity: 0.85 }}>
            {lesson.assessmentPassed
              ? 'You have already passed this quiz. You can review your answers.'
              : 'Answer the questions to complete this lesson.'}
          </p>
        </div>
      </div>
    );
  }

  return (
    <article style={{ padding: '32px 22px 12px' }}>
      <div className="ln-prose" style={{ marginInline: 'auto' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', marginBottom: '0.6em' }}>{lesson.title}</h1>
        {lesson.contentHtml ? (
          // Authored by instructors inside the tenant, and the API strips nothing,
          // so this is trusted-but-internal content — the same trust level as a CMS.
          <div dangerouslySetInnerHTML={{ __html: lesson.contentHtml }} />
        ) : (
          <p style={{ color: 'var(--muted-foreground)' }}>
            This lesson has no content yet.
          </p>
        )}
      </div>
    </article>
  );
}
