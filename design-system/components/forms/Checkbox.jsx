import React from 'react';

/**
 * Checkbox — supports the indeterminate state, which the bulk-select
 * header on every admin table needs (some-but-not-all rows selected).
 *
 * The real <input> stays in the DOM at opacity 0 layered over the visual
 * box, so focus, keyboard toggling, form submission and screen-reader
 * semantics are all the browser's job rather than reimplemented ARIA.
 */
export function Checkbox({
  checked = false,
  indeterminate = false,
  disabled = false,
  label,
  description,
  id,
  onCheckedChange,
  style,
  ...props
}) {
  const ref = React.useRef(null);
  const reactId = React.useId ? React.useId() : 'ln-cb';
  const cbId = id || reactId;

  React.useEffect(() => {
    if (ref.current) ref.current.indeterminate = indeterminate && !checked;
  }, [indeterminate, checked]);

  const on = checked || indeterminate;

  return (
    <div style={{ display: 'flex', gap: 10, alignItems: description ? 'flex-start' : 'center', ...style }}>
      <span style={{ position: 'relative', display: 'inline-flex', flexShrink: 0, marginTop: description ? 2 : 0 }}>
        <input
          ref={ref}
          id={cbId}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onCheckedChange?.(e.target.checked)}
          style={{
            position: 'absolute', inset: 0, width: 18, height: 18,
            margin: 0, opacity: 0, cursor: disabled ? 'not-allowed' : 'pointer',
          }}
          {...props}
        />
        <span
          aria-hidden="true"
          style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 18, height: 18,
            borderRadius: 'var(--radius-xs)',
            background: on ? 'var(--primary)' : 'var(--card)',
            border: `1px solid ${on ? 'var(--primary)' : 'var(--input)'}`,
            color: 'var(--primary-foreground)',
            transition: 'background var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out)',
            opacity: disabled ? 0.5 : 1,
          }}
        >
          {indeterminate && !checked ? (
            <svg width="10" height="10" viewBox="0 0 10 10">
              <path d="M2 5h6" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
            </svg>
          ) : checked ? (
            <svg width="11" height="11" viewBox="0 0 11 11">
              <path d="M2 5.6 L4.3 8 L9 3" fill="none" stroke="currentColor"
                    strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          ) : null}
        </span>
      </span>
      {(label || description) && (
        <label htmlFor={cbId} style={{ cursor: disabled ? 'not-allowed' : 'pointer', lineHeight: 'var(--leading-normal)' }}>
          {label && (
            <span style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--foreground)' }}>{label}</span>
          )}
          {description && (
            <span style={{ display: 'block', fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)', marginTop: 2 }}>
              {description}
            </span>
          )}
        </label>
      )}
    </div>
  );
}
