import React from 'react';

/**
 * Textarea — multi-line input for discussion posts, course summaries and
 * essay answers. Optional `maxLength` renders a live counter that turns
 * destructive inside the last 10% rather than only at the limit, so a
 * learner writing an essay answer gets warning before truncation.
 */
export function Textarea({
  invalid = false,
  disabled = false,
  maxLength,
  value,
  defaultValue,
  rows = 4,
  id,
  style,
  ...props
}) {
  const [focus, setFocus] = React.useState(false);
  const [internal, setInternal] = React.useState(defaultValue ?? '');
  const current = value !== undefined ? value : internal;
  const used = String(current ?? '').length;
  const nearLimit = maxLength ? used >= maxLength * 0.9 : false;

  const borderColor = invalid ? 'var(--destructive)' : focus ? 'var(--ring)' : 'var(--input)';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
      <textarea
        id={id}
        rows={rows}
        disabled={disabled}
        maxLength={maxLength}
        value={value}
        defaultValue={value === undefined ? defaultValue : undefined}
        aria-invalid={invalid || undefined}
        onChange={(e) => { if (value === undefined) setInternal(e.target.value); props.onChange?.(e); }}
        onFocus={(e) => { setFocus(true); props.onFocus?.(e); }}
        onBlur={(e) => { setFocus(false); props.onBlur?.(e); }}
        style={{
          width: '100%', padding: '10px 12px',
          background: disabled ? 'var(--muted)' : 'var(--card)',
          color: 'var(--foreground)',
          border: `1px solid ${borderColor}`,
          borderRadius: 'var(--radius-md)',
          fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
          lineHeight: 'var(--leading-normal)',
          outline: 'none', resize: 'vertical', minHeight: 72,
          boxShadow: focus
            ? `0 0 0 3px oklch(from ${invalid ? 'var(--destructive)' : 'var(--ring)'} l c h / 0.18)`
            : 'var(--shadow-xs)',
          transition: 'border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out)',
          opacity: disabled ? 0.6 : 1,
          ...style,
        }}
        {...props}
      />
      {maxLength && (
        <span style={{
          alignSelf: 'flex-end', fontSize: 'var(--text-xs)',
          fontVariantNumeric: 'tabular-nums',
          color: nearLimit ? 'var(--destructive)' : 'var(--muted-foreground)',
        }}>
          {used} / {maxLength}
        </span>
      )}
    </div>
  );
}
