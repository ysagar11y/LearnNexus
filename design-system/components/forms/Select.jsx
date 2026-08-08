import React from 'react';

/**
 * Select — native <select> under a styled shell.
 *
 * Deliberately native rather than a custom listbox: on mobile this gets
 * the OS picker for free, it is keyboard- and screen-reader-correct with
 * no ARIA authoring, and it cannot be clipped by an overflow ancestor.
 * A custom listbox is only worth its cost when you need multi-select,
 * search or rich option rows — reach for a Combobox there instead.
 */
export function Select({
  options = [],
  value,
  onValueChange,
  placeholder = 'Select…',
  invalid = false,
  disabled = false,
  id,
  style,
  ...props
}) {
  const [focus, setFocus] = React.useState(false);
  const borderColor = invalid ? 'var(--destructive)' : focus ? 'var(--ring)' : 'var(--input)';
  const isPlaceholder = value === undefined || value === '';

  return (
    <div
      style={{
        position: 'relative', display: 'flex', alignItems: 'center',
        height: 38, width: '100%',
        background: disabled ? 'var(--muted)' : 'var(--card)',
        border: `1px solid ${borderColor}`,
        borderRadius: 'var(--radius-md)',
        boxShadow: focus ? `0 0 0 3px oklch(from var(--ring) l c h / 0.18)` : 'var(--shadow-xs)',
        transition: 'border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out)',
        opacity: disabled ? 0.6 : 1,
        ...style,
      }}
    >
      <select
        id={id}
        value={value ?? ''}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        onChange={(e) => onValueChange?.(e.target.value)}
        onFocus={() => setFocus(true)}
        onBlur={() => setFocus(false)}
        style={{
          appearance: 'none', WebkitAppearance: 'none',
          width: '100%', height: '100%',
          padding: '0 34px 0 12px',
          border: 'none', outline: 'none', background: 'transparent',
          // Same reason as Input: the shell draws the focus ring, and base.css's
          // global :focus-visible box-shadow would nest a second one inside it.
          boxShadow: 'none',
          fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
          color: isPlaceholder ? 'var(--muted-foreground)' : 'var(--foreground)',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
        {...props}
      >
        <option value="" disabled>{placeholder}</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
      <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"
           style={{ position: 'absolute', right: 11, pointerEvents: 'none', color: 'var(--muted-foreground)' }}>
        <path d="M3.5 5.5 L7 9 L10.5 5.5" fill="none" stroke="currentColor"
              strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  );
}
