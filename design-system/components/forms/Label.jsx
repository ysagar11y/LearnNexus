import React from 'react';

/**
 * Label — form field label.
 *
 * `required` renders a marked asterisk with an accessible name rather
 * than a bare "*", which screen readers otherwise announce as "star" or
 * skip entirely. `hint` is for the short qualifier that would otherwise
 * end up as placeholder text — placeholders disappear on focus and are
 * the wrong place for anything a user needs while typing.
 */
export function Label({ children, required = false, hint, htmlFor, style, ...props }) {
  return (
    <label
      htmlFor={htmlFor}
      style={{
        display: 'flex', alignItems: 'baseline', gap: 6,
        fontFamily: 'var(--font-sans)',
        fontSize: 'var(--text-sm)',
        fontWeight: 'var(--font-weight-medium)',
        color: 'var(--foreground)',
        ...style,
      }}
      {...props}
    >
      <span>{children}</span>
      {required && (
        <span style={{ color: 'var(--destructive)', lineHeight: 1 }}>
          <span aria-hidden="true">*</span>
          <span className="ln-sr-only"> (required)</span>
        </span>
      )}
      {hint && (
        <span style={{ marginInlineStart: 'auto', fontSize: 'var(--text-xs)',
                       fontWeight: 'var(--font-weight-normal)', color: 'var(--muted-foreground)' }}>
          {hint}
        </span>
      )}
    </label>
  );
}
