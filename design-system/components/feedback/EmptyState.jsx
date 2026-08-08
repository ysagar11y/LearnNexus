import React from 'react';

/**
 * EmptyState — what a learner or admin sees before there is data.
 *
 * This is the most under-designed screen in most LMS products and the
 * one with the highest leverage on retention: a new tenant's very first
 * impression is an empty catalog. So every empty state here is required
 * to carry an action, not just an apology.
 *
 * The illustration is a soft brand-tinted glyph rather than a stock
 * cartoon — it survives tenant re-theming, and it does not date.
 */
export function EmptyState({
  title,
  description,
  action,
  secondaryAction,
  icon,
  compact = false,
  style,
  ...props
}) {
  return (
    <div
      style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        textAlign: 'center',
        padding: compact ? 'var(--space-8) var(--space-5)' : 'var(--space-16) var(--space-6)',
        ...style,
      }}
      {...props}
    >
      <div
        aria-hidden="true"
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          width: compact ? 44 : 64, height: compact ? 44 : 64,
          borderRadius: 'var(--radius-2xl)',
          background: 'var(--primary-soft)',
          color: 'var(--primary)',
          marginBottom: 'var(--space-5)',
        }}
      >
        {icon || <DefaultGlyph size={compact ? 22 : 30} />}
      </div>

      <h3 style={{
        fontSize: compact ? 'var(--text-base)' : 'var(--text-lg)',
        fontWeight: 'var(--font-weight-semibold)',
        color: 'var(--foreground)',
      }}>
        {title}
      </h3>

      {description && (
        <p style={{
          marginTop: 'var(--space-2)',
          maxWidth: 'var(--measure-narrow)',
          fontSize: 'var(--text-sm)',
          lineHeight: 'var(--leading-normal)',
          color: 'var(--muted-foreground)',
        }}>
          {description}
        </p>
      )}

      {(action || secondaryAction) && (
        <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-5)', flexWrap: 'wrap',
                      justifyContent: 'center' }}>
          {action}
          {secondaryAction}
        </div>
      )}
    </div>
  );
}

function DefaultGlyph({ size = 30 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 30 30" fill="none">
      <rect x="4" y="6.5" width="22" height="17" rx="2.5"
            stroke="currentColor" strokeWidth="1.8" opacity="0.5" />
      <path d="M15 6.5v17" stroke="currentColor" strokeWidth="1.8" opacity="0.5" />
      <path d="M8 11.5h4M8 15h4M18 11.5h4M18 15h4"
            stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" opacity="0.75" />
    </svg>
  );
}
