import React from 'react';

/**
 * StatTile — one number, said once.
 *
 * The display serif on the value is the whole idea: dashboards made of
 * bold-sans numbers all look identical, and the serif costs nothing to
 * read at 30px+ while making the figure feel authored. Everything
 * around it stays small and quiet so the number is unambiguously the
 * focal point.
 *
 * `delta` is signed. Direction colour is deliberately *not* automatic —
 * a rising "overdue enrollments" count is bad news, so callers pass
 * `deltaTone` when up ≠ good.
 */
export function StatTile({
  label,
  value,
  unit,
  delta,
  deltaTone,
  caption,
  icon,
  style,
  ...props
}) {
  const hasDelta = delta !== undefined && delta !== null;
  const up = Number(delta) > 0;
  const tone = deltaTone || (up ? 'positive' : Number(delta) < 0 ? 'negative' : 'neutral');
  const deltaColor = tone === 'positive' ? 'var(--success)'
                   : tone === 'negative' ? 'var(--destructive)'
                   : 'var(--muted-foreground)';

  return (
    <div
      style={{
        display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
        padding: 'var(--space-5)',
        background: 'var(--card)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        boxShadow: 'var(--shadow-sm)',
        ...style,
      }}
      {...props}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {icon && (
          <span style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 26, height: 26, borderRadius: 'var(--radius-sm)',
            background: 'var(--primary-soft)', color: 'var(--primary-soft-foreground)',
          }} aria-hidden="true">
            {icon}
          </span>
        )}
        <span style={{
          fontSize: 'var(--text-xs)', fontWeight: 'var(--font-weight-medium)',
          letterSpacing: 'var(--tracking-wide)', color: 'var(--muted-foreground)',
        }}>
          {label}
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
        <span style={{
          fontFamily: 'var(--font-display)',
          fontSize: 'var(--text-3xl)',
          fontWeight: 'var(--font-weight-normal)',
          letterSpacing: 'var(--tracking-display)',
          lineHeight: 1,
          color: 'var(--foreground)',
          fontVariantNumeric: 'tabular-nums',
        }}>
          {value}
          {unit && (
            <span style={{ fontSize: '0.5em', marginInlineStart: 2, color: 'var(--muted-foreground)' }}>
              {unit}
            </span>
          )}
        </span>

        {hasDelta && (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 3,
            fontSize: 'var(--text-xs)', fontWeight: 'var(--font-weight-medium)',
            color: deltaColor, fontVariantNumeric: 'tabular-nums',
          }}>
            <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true"
                 style={{ transform: up ? 'none' : 'rotate(180deg)' }}>
              <path d="M5 2 L8.5 7 L1.5 7 Z" fill="currentColor" />
            </svg>
            {Math.abs(Number(delta))}%
          </span>
        )}
      </div>

      {caption && (
        <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
          {caption}
        </span>
      )}
    </div>
  );
}
