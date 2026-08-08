import React from 'react';

/**
 * Switch — for settings that apply immediately (tenant feature flags,
 * notification preferences, "publish this course").
 *
 * Use Checkbox instead when the change only takes effect on submit.
 * That distinction is the entire reason both exist: a switch that needs
 * a Save button afterwards is a lie about immediacy.
 */
export function Switch({
  checked = false,
  disabled = false,
  label,
  description,
  id,
  onCheckedChange,
  style,
  ...props
}) {
  const reactId = React.useId ? React.useId() : 'ln-sw';
  const swId = id || reactId;

  return (
    <div style={{ display: 'flex', gap: 12, alignItems: description ? 'flex-start' : 'center', ...style }}>
      <span style={{ position: 'relative', display: 'inline-flex', flexShrink: 0, marginTop: description ? 1 : 0 }}>
        <input
          id={swId}
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onCheckedChange?.(e.target.checked)}
          style={{
            position: 'absolute', inset: 0, width: 36, height: 20,
            margin: 0, opacity: 0, cursor: disabled ? 'not-allowed' : 'pointer', zIndex: 1,
          }}
          {...props}
        />
        <span
          aria-hidden="true"
          style={{
            display: 'inline-flex', alignItems: 'center',
            width: 36, height: 20, padding: 2,
            borderRadius: 'var(--radius-full)',
            background: checked ? 'var(--primary)' : 'var(--border-strong)',
            transition: 'background var(--duration-base) var(--ease-out)',
            opacity: disabled ? 0.5 : 1,
          }}
        >
          <span style={{
            width: 16, height: 16, borderRadius: 'var(--radius-full)',
            background: 'var(--card)', boxShadow: 'var(--shadow-sm)',
            transform: checked ? 'translateX(16px)' : 'translateX(0)',
            transition: 'transform var(--duration-base) var(--ease-out)',
          }} />
        </span>
      </span>
      {(label || description) && (
        <label htmlFor={swId} style={{ cursor: disabled ? 'not-allowed' : 'pointer' }}>
          {label && (
            <span style={{ display: 'block', fontSize: 'var(--text-sm)',
                           fontWeight: 'var(--font-weight-medium)', color: 'var(--foreground)' }}>
              {label}
            </span>
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
