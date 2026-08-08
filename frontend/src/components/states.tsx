import type { ReactNode } from 'react';
import { Alert } from '@ds/components/feedback/Alert';
import { Button } from '@ds/components/forms/Button';
import { Skeleton } from '@ds/components/feedback/Skeleton';

import { ApiError } from '@/lib/api';
import { IconLogo } from './icons';
import { DEMO_WORKSPACES, WorkspacePicker } from './WorkspacePicker';

/** Full-viewport loading state, used while the session and tenant resolve. */
export function FullPageSpinner({ inline = false }: { inline?: boolean }) {
  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        minHeight: inline ? '40vh' : '100dvh',
        display: 'grid',
        placeItems: 'center',
        background: inline ? 'transparent' : 'var(--background)',
        color: 'var(--muted-foreground)',
        gap: 12,
      }}
    >
      <div className="stack stack-3" style={{ alignItems: 'center' }}>
        <IconLogo size={30} style={{ color: 'var(--primary)', opacity: 0.8 }} />
        <span style={{ fontSize: 'var(--text-sm)' }}>Loading…</span>
      </div>
    </div>
  );
}

/** Skeleton that mirrors the footprint of what it replaces, per the design system. */
export function LoadingRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="surface" aria-hidden="true">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="surface-row">
          <Skeleton width={38} height={38} radius="var(--radius-md)" />
          <div className="stack stack-2" style={{ flex: 1 }}>
            <Skeleton width="42%" height={12} />
            <Skeleton width="24%" height={10} />
          </div>
          <Skeleton width={72} height={26} radius="var(--radius-md)" />
        </div>
      ))}
    </div>
  );
}

export function LoadingCards({ count = 6 }: { count?: number }) {
  return (
    <div className="card-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="surface" style={{ padding: 0 }}>
          <Skeleton height={150} radius={0} />
          <div className="stack stack-2" style={{ padding: 15 }}>
            <Skeleton width="34%" height={10} />
            <Skeleton width="82%" height={14} />
            <Skeleton width="56%" height={10} />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * One place that turns a thrown error into something a person can act on.
 * Permission and not-found failures get their own wording because "something
 * went wrong" is actively unhelpful when the cause is known.
 */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null;

  const title =
    apiError?.status === 403
      ? 'You do not have access to this'
      : apiError?.status === 404
        ? 'Not found'
        : 'Something went wrong';

  const message =
    apiError?.message ??
    (error instanceof Error ? error.message : 'An unexpected error occurred.');

  return (
    <Alert
      tone={apiError?.status === 403 ? 'warning' : 'critical'}
      title={title}
      action={
        onRetry ? (
          <Button size="sm" variant="outline" onClick={onRetry}>
            Try again
          </Button>
        ) : undefined
      }
    >
      {message}
    </Alert>
  );
}

/**
 * A dead end that has to be escapable. The most likely reason someone lands
 * here is that they typed an email into the workspace box, so the recovery is
 * another go at the address — inline, right here — not a link back to the start.
 */
export function WorkspaceNotFound({ slug }: { slug: string }) {
  return (
    <div
      className="ln-wash"
      style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', padding: 24 }}
    >
      <div className="ln-panel" style={{ maxWidth: 520, textAlign: 'center' }}>
        <IconLogo size={30} style={{ color: 'var(--primary)', margin: '0 auto 14px' }} />
        <h1 style={{ fontSize: 'var(--text-xl)', marginBottom: 8 }}>No workspace here</h1>
        <p style={{ fontSize: 'var(--text-sm)', opacity: 0.85, marginBottom: 6 }}>
          There is no workspace called <strong>{slug}</strong>.
        </p>
        <p style={{ fontSize: 'var(--text-sm)', opacity: 0.75, marginBottom: 20 }}>
          A workspace address is your organisation's short name, like <code>acme</code> — not your
          email address.
        </p>

        <WorkspacePicker size="md" autoFocus suggestions={DEMO_WORKSPACES} />

        <div style={{ marginTop: 20 }}>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              localStorage.removeItem('ln.tenant');
              window.location.assign('/welcome');
            }}
          >
            Back to LearnNexus
          </Button>
        </div>
      </div>
    </div>
  );
}

/** Page heading with optional actions. */
export function PageHeader({
  title,
  subtitle,
  actions,
  display = false,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  /** Uses the display serif — reserved for the learner greeting. */
  display?: boolean;
}) {
  return (
    <header className="page-header">
      <div style={{ minWidth: 0 }}>
        <h1 className={display ? 'greeting' : 'page-title'}>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}
