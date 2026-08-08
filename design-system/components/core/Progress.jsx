import React from 'react';

/**
 * Progress — linear completion bar. This is the most-repeated element in
 * the product (every enrollment row carries one), so it is deliberately
 * quiet: a hairline rail, no gradient, no stripes, no animation on idle.
 *
 * At 100% it switches to the success hue. That is the only colour change
 * in the component, and it exists so a learner scanning a list can find
 * finished courses without reading a single number.
 */
export function Progress({
  value = 0,
  size = 'md',
  showLabel = false,
  label,
  tone,
  style,
  ...props
}) {
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  const done = pct >= 100;
  const height = { xs: 3, sm: 5, md: 7, lg: 10 }[size] || 7;

  const fill = tone === 'accent' ? 'var(--accent)'
             : tone === 'warning' ? 'var(--warning)'
             : done ? 'var(--success)' : 'var(--primary)';

  return (
    <div style={{ width: '100%', ...style }} {...props}>
      {(showLabel || label) && (
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
          marginBottom: 6, fontSize: 'var(--text-xs)',
        }}>
          <span style={{ color: 'var(--muted-foreground)' }}>{label ?? 'Progress'}</span>
          <span style={{ color: done ? 'var(--success)' : 'var(--foreground)',
                         fontWeight: 'var(--font-weight-medium)', fontVariantNumeric: 'tabular-nums' }}>
            {pct}%
          </span>
        </div>
      )}
      <div
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label ?? 'Progress'}
        style={{
          height, width: '100%',
          background: 'var(--track)',
          borderRadius: 'var(--radius-full)',
          overflow: 'hidden',
        }}
      >
        <div style={{
          height: '100%', width: `${pct}%`,
          background: fill,
          borderRadius: 'var(--radius-full)',
          transition: 'width var(--duration-slower) var(--ease-out), background var(--duration-base) var(--ease-out)',
        }} />
      </div>
    </div>
  );
}

/**
 * ProgressRing — the same data as a dial, for course headers and the
 * dashboard's single headline metric. Uses one SVG circle with a dash
 * offset rather than two stacked arcs, so it stays crisp at any size.
 */
export function ProgressRing({
  value = 0,
  size = 72,
  thickness = 6,
  showValue = true,
  label,
  style,
}) {
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  const done = pct >= 100;
  const r = (size - thickness) / 2;
  const circumference = 2 * Math.PI * r;

  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0, ...style }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}
           role="img" aria-label={`${label ? label + ': ' : ''}${pct}% complete`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none"
                stroke="var(--track)" strokeWidth={thickness} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none"
                stroke={done ? 'var(--success)' : 'var(--primary)'}
                strokeWidth={thickness} strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={circumference * (1 - pct / 100)}
                style={{ transition: 'stroke-dashoffset var(--duration-slower) var(--ease-out), stroke var(--duration-base) var(--ease-out)' }} />
      </svg>
      {showValue && (
        <div style={{
          position: 'absolute', inset: 0,
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
          fontVariantNumeric: 'tabular-nums',
        }}>
          <span style={{
            fontSize: size * 0.27, fontWeight: 'var(--font-weight-semibold)',
            letterSpacing: 'var(--tracking-tight)',
            color: done ? 'var(--success)' : 'var(--foreground)', lineHeight: 1,
          }}>
            {pct}
            <span style={{ fontSize: '0.6em', fontWeight: 'var(--font-weight-medium)' }}>%</span>
          </span>
        </div>
      )}
    </div>
  );
}
