import React from 'react';

/**
 * Avatar — user identity chip.
 *
 * Fallback initials are tinted by hashing the name onto the chart hue
 * ramp, so the same person is always the same colour across the product
 * and a roster of 30 learners is scannable. The hash is deterministic
 * and hue-only: lightness and chroma stay fixed, so every generated
 * tint keeps the same contrast against its foreground.
 */
function initialsOf(name = '') {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function hueOf(seed = '') {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360;
  return h;
}

const SIZES = { xs: 20, sm: 26, md: 32, lg: 40, xl: 56, '2xl': 80 };

export function Avatar({ src, name = '', size = 'md', status, style, ...props }) {
  const [broken, setBroken] = React.useState(false);
  const px = SIZES[size] || SIZES.md;
  const hue = hueOf(name);
  const showImg = src && !broken;

  return (
    <span
      style={{ position: 'relative', display: 'inline-flex', flexShrink: 0, ...style }}
      {...props}
    >
      <span
        title={name || undefined}
        style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: px, height: px, borderRadius: 'var(--radius-full)',
          overflow: 'hidden',
          background: showImg ? 'var(--muted)' : `oklch(0.88 0.055 ${hue})`,
          color: `oklch(0.34 0.09 ${hue})`,
          fontSize: Math.max(9, Math.round(px * 0.38)),
          fontWeight: 'var(--font-weight-semibold)',
          fontFamily: 'var(--font-sans)',
          letterSpacing: '0.01em',
          userSelect: 'none',
          boxShadow: 'inset 0 0 0 1px oklch(0.5 0.02 250 / 0.08)',
        }}
      >
        {showImg
          ? <img src={src} alt={name} onError={() => setBroken(true)}
                 style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          : <span aria-hidden="true">{initialsOf(name)}</span>}
      </span>
      {status && (
        <span
          aria-label={status}
          style={{
            position: 'absolute', right: -1, bottom: -1,
            width: Math.max(7, px * 0.28), height: Math.max(7, px * 0.28),
            borderRadius: 'var(--radius-full)',
            background: status === 'online' ? 'var(--success)'
                      : status === 'away' ? 'var(--warning)' : 'var(--muted-foreground)',
            border: '2px solid var(--card)',
          }}
        />
      )}
    </span>
  );
}

/** Overlapping stack for "who else is on this course". */
export function AvatarGroup({ users = [], max = 4, size = 'sm' }) {
  const px = SIZES[size] || SIZES.sm;
  const shown = users.slice(0, max);
  const extra = users.length - shown.length;

  return (
    <div style={{ display: 'flex', alignItems: 'center' }}>
      {shown.map((u, i) => (
        <span key={u.id ?? u.name ?? i}
              style={{ marginInlineStart: i === 0 ? 0 : -px * 0.3, borderRadius: 'var(--radius-full)',
                       boxShadow: '0 0 0 2px var(--card)' }}>
          <Avatar name={u.name} src={u.avatarUrl} size={size} />
        </span>
      ))}
      {extra > 0 && (
        <span style={{
          marginInlineStart: -px * 0.3,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: px, height: px, borderRadius: 'var(--radius-full)',
          background: 'var(--muted)', color: 'var(--muted-foreground)',
          fontSize: Math.max(9, Math.round(px * 0.36)), fontWeight: 'var(--font-weight-medium)',
          boxShadow: '0 0 0 2px var(--card)',
        }}>
          +{extra}
        </span>
      )}
    </div>
  );
}
