import React from 'react';

/**
 * Skeleton — loading placeholder.
 *
 * A slow left-to-right sheen, not a pulsing opacity fade. Pulsing draws
 * the eye rhythmically and makes a loading dashboard genuinely
 * unpleasant when six of them beat out of phase; a directional sheen
 * reads as "in transit" and stays calm in bulk.
 *
 * Skeletons must mirror the shape of the content they replace, or the
 * layout shift on load is worse than a spinner would have been.
 */
export function Skeleton({ width = '100%', height = 12, radius = 'var(--radius-sm)', style, ...props }) {
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block', width, height, borderRadius: radius,
        // The mid-stop must be LIGHTER than the ends or the sheen reads as a
        // dark band travelling backwards. Mixing two alpha surface tints
        // averages them and produces exactly that, so the highlight is an
        // explicit step up the tint ladder instead.
        background:
          'linear-gradient(90deg, var(--muted) 0%, var(--surface-active) 50%, var(--muted) 100%)',
        backgroundSize: '200% 100%',
        animation: 'ln-shimmer 1.4s var(--ease-in-out) infinite',
        ...style,
      }}
      {...props}
    >
      <style>{'@keyframes ln-shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}'}</style>
    </span>
  );
}

/** Matches the CourseCard footprint so the grid does not jump on load. */
export function SkeletonCourseCard({ style }) {
  return (
    <div
      aria-hidden="true"
      style={{
        background: 'var(--card)', border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)', overflow: 'hidden', ...style,
      }}
    >
      <Skeleton height={132} radius="0" />
      <div style={{ padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Skeleton width="38%" height={10} radius="var(--radius-full)" />
        <Skeleton width="88%" height={15} />
        <Skeleton width="62%" height={15} />
        <div style={{ display: 'flex', gap: 8, marginTop: 6, alignItems: 'center' }}>
          <Skeleton width={24} height={24} radius="var(--radius-full)" />
          <Skeleton width="42%" height={10} />
        </div>
      </div>
    </div>
  );
}

/** Rows for admin tables. */
export function SkeletonRows({ rows = 5, columns = 4, style }) {
  return (
    <div aria-hidden="true" style={{ display: 'flex', flexDirection: 'column', ...style }}>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} style={{
          display: 'grid',
          gridTemplateColumns: `minmax(0,2fr) repeat(${Math.max(0, columns - 1)}, minmax(0,1fr))`,
          gap: 'var(--space-4)', alignItems: 'center',
          padding: 'var(--space-4) 0',
          borderBottom: '1px solid var(--border)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Skeleton width={28} height={28} radius="var(--radius-full)" />
            <Skeleton width={`${52 + ((r * 13) % 26)}%`} height={11} />
          </div>
          {Array.from({ length: Math.max(0, columns - 1) }).map((__, c) => (
            <Skeleton key={c} width={`${44 + ((r + c) * 11) % 34}%`} height={11} />
          ))}
        </div>
      ))}
    </div>
  );
}
