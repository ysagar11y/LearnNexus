import React from 'react';

/**
 * Badge — compact status token.
 *
 * The `status` prop maps the enum values that actually exist in the
 * schema (course status, enrollment status, attempt status, invoice
 * status) onto tones, so a caller passes the raw DB value and gets the
 * right treatment without a lookup table at every call site. Keeping
 * that mapping here is what stops PUBLISHED being green on one screen
 * and blue on another.
 */
const TONES = {
  neutral: { bg: 'var(--muted)',        fg: 'var(--muted-foreground)', dot: 'var(--muted-foreground)' },
  brand:   { bg: 'var(--primary-soft)', fg: 'var(--primary-soft-foreground)', dot: 'var(--primary)' },
  success: { bg: 'var(--success-soft)', fg: 'var(--success)',  dot: 'var(--success)' },
  warning: { bg: 'var(--warning-soft)', fg: 'var(--warning)',  dot: 'var(--warning)' },
  danger:  { bg: 'var(--danger-soft)',  fg: 'var(--destructive)', dot: 'var(--destructive)' },
  info:    { bg: 'var(--info-soft)',    fg: 'var(--info)',     dot: 'var(--info)' },
  accent:  { bg: 'var(--accent-soft)',  fg: 'var(--accent-foreground)', dot: 'var(--accent)' },
};

const STATUS_TONE = {
  // courses.status
  DRAFT: 'neutral', IN_REVIEW: 'warning', PUBLISHED: 'success', ARCHIVED: 'neutral',
  // enrollments.status
  ACTIVE: 'brand', COMPLETED: 'success', EXPIRED: 'danger',
  WITHDRAWN: 'neutral', WAITLISTED: 'warning',
  // attempts.status
  IN_PROGRESS: 'brand', SUBMITTED: 'info', GRADED: 'success',
  // users.status
  INVITED: 'warning', SUSPENDED: 'danger',
  // tenants.status / subscriptions.status
  TRIAL: 'accent', TRIALING: 'accent', PAST_DUE: 'danger', CANCELED: 'neutral',
  // invoices.status
  OPEN: 'info', PAID: 'success', VOID: 'neutral',
};

/** DRAFT -> "Draft", IN_REVIEW -> "In review" */
function humanise(s) {
  return String(s).toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
}

export function Badge({
  children,
  status,
  tone,
  size = 'md',
  dot = false,
  style,
  ...props
}) {
  const resolved = tone || (status ? STATUS_TONE[status] || 'neutral' : 'neutral');
  const t = TONES[resolved] || TONES.neutral;
  const s = size === 'sm'
    ? { height: 18, paddingInline: 6, fontSize: 'var(--text-2xs)' }
    : { height: 22, paddingInline: 8, fontSize: 'var(--text-xs)' };

  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 5,
        background: t.bg, color: t.fg,
        borderRadius: 'var(--radius-full)',
        fontFamily: 'var(--font-sans)',
        fontWeight: 'var(--font-weight-medium)',
        lineHeight: 1, whiteSpace: 'nowrap',
        ...s, ...style,
      }}
      {...props}
    >
      {dot && (
        <span aria-hidden="true" style={{
          width: 5, height: 5, borderRadius: 'var(--radius-full)',
          background: t.dot, flexShrink: 0,
        }} />
      )}
      {children ?? (status ? humanise(status) : null)}
    </span>
  );
}
