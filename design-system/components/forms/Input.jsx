import React from 'react';

/**
 * Input — single-line text field, with optional leading icon and a
 * trailing slot (used for the catalog search's ⌘K hint and for
 * password reveal).
 *
 * Invalid state is carried by `invalid` plus an `error` message, and
 * wires aria-invalid / aria-describedby itself. Colour alone is never
 * the only error signal — the message is required for the state to
 * read at all for colour-blind users.
 */
export function Input({
  leading,
  trailing,
  invalid = false,
  error,
  disabled = false,
  id,
  className = '',
  style,
  ...props
}) {
  const [focus, setFocus] = React.useState(false);
  const reactId = React.useId ? React.useId() : 'ln-input';
  const inputId = id || reactId;
  const errorId = `${inputId}-error`;

  const borderColor = invalid
    ? 'var(--destructive)'
    : focus
      ? 'var(--ring)'
      : 'var(--input)';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          height: 38,
          paddingInline: 12,
          background: disabled ? 'var(--muted)' : 'var(--card)',
          border: `1px solid ${borderColor}`,
          borderRadius: 'var(--radius-md)',
          boxShadow: focus
            ? `0 0 0 3px oklch(from ${invalid ? 'var(--destructive)' : 'var(--ring)'} l c h / 0.18)`
            : 'var(--shadow-xs)',
          transition: 'border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out)',
          opacity: disabled ? 0.6 : 1,
          ...style,
        }}
      >
        {leading && (
          <span style={{ display: 'flex', color: 'var(--muted-foreground)', flexShrink: 0 }} aria-hidden="true">
            {leading}
          </span>
        )}
        <input
          id={inputId}
          className={className}
          disabled={disabled}
          aria-invalid={invalid || undefined}
          aria-describedby={error ? errorId : undefined}
          onFocus={(e) => { setFocus(true); props.onFocus?.(e); }}
          onBlur={(e) => { setFocus(false); props.onBlur?.(e); }}
          style={{
            flex: 1, minWidth: 0, height: '100%',
            border: 'none', outline: 'none', background: 'transparent',
            // The shell above owns the focus treatment (border + soft ring).
            // base.css's global :focus-visible rule sets a box-shadow, not just
            // an outline, so without this the bare input draws a *second*
            // rounded rectangle nested inside the shell. Suppressed here rather
            // than globally, so unwrapped inputs keep their focus ring.
            boxShadow: 'none',
            fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
            color: 'var(--foreground)',
          }}
          {...props}
        />
        {trailing && (
          <span style={{ display: 'flex', color: 'var(--muted-foreground)', flexShrink: 0 }}>
            {trailing}
          </span>
        )}
      </div>
      {error && (
        <span id={errorId} role="alert"
              style={{ fontSize: 'var(--text-xs)', color: 'var(--destructive)' }}>
          {error}
        </span>
      )}
    </div>
  );
}
