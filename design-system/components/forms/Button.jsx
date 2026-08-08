import React from 'react';

/**
 * Button — the one action primitive.
 *
 * Sizing note: default height is 36px, not the 32px that dense admin
 * tools favour. LearnNexus is used for hours at a time by non-technical
 * learners, and the extra 4px measurably reduces mis-clicks without
 * making dashboards feel sparse. `sm` exists for toolbars and table
 * rows; it is the exception, not the density default.
 */

const VARIANTS = {
  primary: {
    background: 'var(--primary)',
    color: 'var(--primary-foreground)',
    border: '1px solid transparent',
    boxShadow: 'var(--shadow-xs)',
  },
  // Warm accent. Reserved for the single highest-intent action on a
  // marketing page — never used inside the app shell.
  accent: {
    background: 'var(--accent)',
    color: 'var(--accent-foreground)',
    border: '1px solid transparent',
    boxShadow: 'var(--shadow-xs)',
  },
  secondary: {
    background: 'var(--secondary)',
    color: 'var(--secondary-foreground)',
    border: '1px solid transparent',
  },
  outline: {
    background: 'var(--card)',
    color: 'var(--foreground)',
    border: '1px solid var(--input)',
    boxShadow: 'var(--shadow-xs)',
  },
  ghost: {
    background: 'transparent',
    color: 'var(--muted-foreground)',
    border: '1px solid transparent',
  },
  destructive: {
    background: 'var(--destructive)',
    color: 'var(--destructive-foreground)',
    border: '1px solid transparent',
  },
  link: {
    background: 'transparent',
    color: 'var(--primary)',
    border: '1px solid transparent',
    textDecoration: 'underline',
    textUnderlineOffset: '3px',
    padding: 0,
    height: 'auto',
  },
};

const HOVER = {
  primary: { background: 'var(--primary-hover)', boxShadow: 'var(--shadow-sm)' },
  accent: { background: 'var(--accent-hover)', boxShadow: 'var(--shadow-sm)' },
  secondary: { background: 'color-mix(in oklch, var(--secondary) 88%, var(--foreground))' },
  outline: { background: 'var(--muted)' },
  ghost: { background: 'var(--muted)', color: 'var(--foreground)' },
  destructive: { background: 'color-mix(in oklch, var(--destructive) 88%, black)' },
  link: { color: 'var(--primary-hover)' },
};

const SIZES = {
  sm:   { height: 32, padding: '0 12px', fontSize: 'var(--text-sm)',   gap: 6, borderRadius: 'var(--radius-md)' },
  md:   { height: 36, padding: '0 16px', fontSize: 'var(--text-sm)',   gap: 8, borderRadius: 'var(--radius-md)' },
  lg:   { height: 44, padding: '0 22px', fontSize: 'var(--text-base)', gap: 8, borderRadius: 'var(--radius-md)' },
  icon: { height: 36, width: 36, padding: 0, gap: 0, borderRadius: 'var(--radius-md)' },
};

export function Button({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  loading = false,
  disabled = false,
  className = '',
  style,
  children,
  ...props
}) {
  const [hover, setHover] = React.useState(false);
  const [active, setActive] = React.useState(false);

  const base = VARIANTS[variant] || VARIANTS.primary;
  const sizing = variant === 'link' ? { fontSize: 'var(--text-sm)', gap: 6 } : SIZES[size];
  const isDead = disabled || loading;

  return (
    <button
      className={className}
      disabled={isDead}
      aria-busy={loading || undefined}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); setActive(false); }}
      onMouseDown={() => setActive(true)}
      onMouseUp={() => setActive(false)}
      style={{
        display: fullWidth ? 'flex' : 'inline-flex',
        width: fullWidth ? '100%' : undefined,
        alignItems: 'center',
        justifyContent: 'center',
        fontFamily: 'var(--font-sans)',
        fontWeight: 'var(--font-weight-medium)',
        lineHeight: 1,
        whiteSpace: 'nowrap',
        cursor: isDead ? 'not-allowed' : 'pointer',
        opacity: isDead ? 0.55 : 1,
        // Transform is the whole press affordance. 1px is enough to feel
        // physical; more reads as a toy.
        transform: active && !isDead ? 'translateY(1px)' : 'translateY(0)',
        transition:
          'background var(--duration-fast) var(--ease-out),' +
          'box-shadow var(--duration-fast) var(--ease-out),' +
          'transform var(--duration-instant) var(--ease-out),' +
          'color var(--duration-fast) var(--ease-out)',
        ...base,
        ...sizing,
        ...(hover && !isDead ? HOVER[variant] : null),
        ...style,
      }}
      {...props}
    >
      {loading && <Spinner />}
      {children}
    </button>
  );
}

function Spinner() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"
         style={{ animation: 'ln-spin 620ms linear infinite', flexShrink: 0 }}>
      <circle cx="7" cy="7" r="5.5" fill="none" stroke="currentColor"
              strokeWidth="2" opacity="0.25" />
      <path d="M7 1.5 A5.5 5.5 0 0 1 12.5 7" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" />
      <style>{'@keyframes ln-spin{to{transform:rotate(360deg)}}'}</style>
    </svg>
  );
}
