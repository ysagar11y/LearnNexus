import React from 'react';

/**
 * Alert — inline banner for page- or section-level messages: an overdue
 * mandatory course, a trial ending, a failed payment.
 *
 * Every tone ships an icon as well as a colour. That redundancy is not
 * decoration — it is the only reason the severity survives for a
 * colour-blind user or a greyscale print of a compliance report.
 *
 * `severity` accepts the notifications.severity enum directly
 * (INFO / SUCCESS / WARNING / CRITICAL).
 */
const TONES = {
  info:     { bg: 'var(--info-soft)',    fg: 'var(--info)',        icon: 'i' },
  success:  { bg: 'var(--success-soft)', fg: 'var(--success)',     icon: 'check' },
  warning:  { bg: 'var(--warning-soft)', fg: 'var(--warning)',     icon: 'alert' },
  critical: { bg: 'var(--danger-soft)',  fg: 'var(--destructive)', icon: 'alert' },
};

const SEVERITY_TONE = { INFO: 'info', SUCCESS: 'success', WARNING: 'warning', CRITICAL: 'critical' };

function Icon({ kind, color }) {
  const common = { width: 16, height: 16, viewBox: '0 0 16 16', 'aria-hidden': true,
                   style: { flexShrink: 0, color, marginTop: 1 } };
  if (kind === 'check') {
    return (
      <svg {...common}>
        <circle cx="8" cy="8" r="7" fill="currentColor" opacity="0.16" />
        <path d="M5 8.2 L7 10.2 L11 5.8" fill="none" stroke="currentColor"
              strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (kind === 'alert') {
    return (
      <svg {...common}>
        <circle cx="8" cy="8" r="7" fill="currentColor" opacity="0.16" />
        <path d="M8 4.4v4.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        <circle cx="8" cy="11.4" r="1" fill="currentColor" />
      </svg>
    );
  }
  return (
    <svg {...common}>
      <circle cx="8" cy="8" r="7" fill="currentColor" opacity="0.16" />
      <path d="M8 7.2v4.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="8" cy="4.6" r="1" fill="currentColor" />
    </svg>
  );
}

export function Alert({
  tone,
  severity,
  title,
  children,
  action,
  onDismiss,
  style,
  ...props
}) {
  const resolved = tone || SEVERITY_TONE[severity] || 'info';
  const t = TONES[resolved] || TONES.info;

  return (
    <div
      role={resolved === 'critical' ? 'alert' : 'status'}
      style={{
        display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start',
        padding: 'var(--space-4)',
        background: t.bg,
        // Left rule rather than a full border: reads as an annotation on
        // the page instead of another competing card.
        borderInlineStart: `3px solid ${t.fg}`,
        borderRadius: 'var(--radius-md)',
        ...style,
      }}
      {...props}
    >
      <Icon kind={t.icon} color={t.fg} />
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--font-weight-semibold)',
                        color: 'var(--foreground)', marginBottom: children ? 3 : 0 }}>
            {title}
          </div>
        )}
        {children && (
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)',
                        lineHeight: 'var(--leading-normal)' }}>
            {children}
          </div>
        )}
        {action && <div style={{ marginTop: 'var(--space-3)' }}>{action}</div>}
      </div>
      {onDismiss && (
        <button
          onClick={onDismiss}
          aria-label="Dismiss"
          style={{
            flexShrink: 0, width: 24, height: 24, display: 'inline-flex',
            alignItems: 'center', justifyContent: 'center',
            border: 'none', background: 'transparent', cursor: 'pointer',
            color: 'var(--muted-foreground)', borderRadius: 'var(--radius-sm)',
          }}
        >
          <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M3 3 L9 9 M9 3 L3 9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
          </svg>
        </button>
      )}
    </div>
  );
}
