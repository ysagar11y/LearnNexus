import React from 'react';

/**
 * Separator — hairline rule, optionally with a centred label ("or
 * continue with", "Older activity").
 *
 * Decorative by default (aria-hidden): a plain rule between two blocks
 * carries no information a screen reader needs, and announcing every one
 * of them is noise. Passing a `label` makes it semantic automatically.
 */
export function Separator({ orientation = 'horizontal', label, style, ...props }) {
  if (orientation === 'vertical') {
    return (
      <div
        aria-hidden="true"
        style={{ width: 1, alignSelf: 'stretch', background: 'var(--border)', flexShrink: 0, ...style }}
        {...props}
      />
    );
  }

  if (label) {
    return (
      <div
        role="separator"
        aria-label={typeof label === 'string' ? label : undefined}
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', ...style }}
        {...props}
      >
        <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
        <span style={{
          fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)',
          whiteSpace: 'nowrap', letterSpacing: 'var(--tracking-wide)',
        }}>
          {label}
        </span>
        <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
      </div>
    );
  }

  return (
    <hr
      aria-hidden="true"
      style={{ height: 1, border: 0, background: 'var(--border)', width: '100%', ...style }}
      {...props}
    />
  );
}
