import React from 'react';

/**
 * LessonRow — one lesson inside a course outline or the player's rail.
 *
 * The status marker on the left is the load-bearing element: a learner
 * scanning a 40-lesson outline is looking for "where was I", and that
 * question has to be answerable without reading a word. Three states —
 * done (filled check), current (ring), locked (padlock) — and the
 * current row additionally carries a brand tint so it is findable after
 * scrolling away and back.
 *
 * `contentType` maps the lessons.content_type enum to a glyph, so a
 * learner can tell a 12-minute video from a quiz before clicking.
 */
function fmtLen(seconds) {
  const s = Math.max(0, Math.round(Number(seconds) || 0));
  if (!s) return null;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h}h ${rem}m` : `${h}h`;
}

const TYPE_GLYPH = {
  VIDEO: <path d="M5.5 4.2 L11 7.5 L5.5 10.8 Z" fill="currentColor" />,
  QUIZ: <><circle cx="7.5" cy="7.5" r="5.4" fill="none" stroke="currentColor" strokeWidth="1.5" />
         <path d="M7.5 4.6v3.4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
         <circle cx="7.5" cy="10.4" r="0.9" fill="currentColor" /></>,
  PDF: <><rect x="3.5" y="2.5" width="8" height="10" rx="1.2" fill="none" stroke="currentColor" strokeWidth="1.4" />
        <path d="M5.6 6h3.8M5.6 8.4h3.8" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" /></>,
  AUDIO: <><path d="M4 6v3M7.5 3.5v8M11 5.5v4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></>,
  HTML: <><rect x="2.8" y="3.5" width="9.4" height="8" rx="1.2" fill="none" stroke="currentColor" strokeWidth="1.4" />
         <path d="M2.8 6h9.4" stroke="currentColor" strokeWidth="1.4" /></>,
  LINK: <path d="M6.2 8.8 L8.8 6.2 M5.2 6.8 L4 8a2.2 2.2 0 0 0 3.1 3.1l1.2-1.2M9.8 8.2 L11 7a2.2 2.2 0 0 0-3.1-3.1L6.7 5.1"
              fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />,
  SCORM: <><rect x="3" y="3" width="9" height="9" rx="1.4" fill="none" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5.6 7.5h3.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" /></>,
};

export function LessonRow({
  lesson = {},
  state = 'todo',       // 'done' | 'current' | 'todo' | 'locked'
  index,
  onOpen,
  style,
  ...props
}) {
  const { title = 'Untitled lesson', contentType = 'HTML', durationSeconds, isPreview } = lesson;
  const [hover, setHover] = React.useState(false);
  const locked = state === 'locked';
  const current = state === 'current';
  const done = state === 'done';
  const len = fmtLen(durationSeconds);

  return (
    <button
      onClick={() => !locked && onOpen?.(lesson)}
      disabled={locked}
      aria-current={current ? 'step' : undefined}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        width: '100%', padding: '10px var(--space-3)',
        border: 'none', borderRadius: 'var(--radius-md)',
        background: current ? 'var(--primary-soft)' : hover && !locked ? 'var(--muted)' : 'transparent',
        textAlign: 'start',
        cursor: locked ? 'not-allowed' : 'pointer',
        opacity: locked ? 0.55 : 1,
        transition: 'background var(--duration-fast) var(--ease-out)',
        ...style,
      }}
      {...props}
    >
      <Marker state={state} index={index} />

      <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span style={{
          fontSize: 'var(--text-sm)',
          fontWeight: current ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
          color: current ? 'var(--primary-soft-foreground)' : done ? 'var(--muted-foreground)' : 'var(--foreground)',
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {title}
        </span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 7,
                       fontSize: 'var(--text-2xs)', color: 'var(--muted-foreground)' }}>
          <svg width="13" height="13" viewBox="0 0 15 15" aria-hidden="true" style={{ flexShrink: 0, opacity: 0.85 }}>
            {TYPE_GLYPH[contentType] || TYPE_GLYPH.HTML}
          </svg>
          <span style={{ textTransform: 'capitalize' }}>{String(contentType).toLowerCase()}</span>
          {len && <><span aria-hidden="true">·</span><span>{len}</span></>}
          {isPreview && (
            <span style={{
              paddingInline: 5, height: 15, display: 'inline-flex', alignItems: 'center',
              borderRadius: 'var(--radius-full)', background: 'var(--accent-soft)',
              color: 'var(--accent-foreground)', fontWeight: 'var(--font-weight-medium)',
            }}>
              Free preview
            </span>
          )}
        </span>
      </span>
    </button>
  );
}

function Marker({ state, index }) {
  const box = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    width: 22, height: 22, borderRadius: 'var(--radius-full)', flexShrink: 0,
    fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
    fontVariantNumeric: 'tabular-nums',
  };

  if (state === 'done') {
    return (
      <span aria-label="Completed" style={{ ...box, background: 'var(--success)', color: 'var(--success-foreground)' }}>
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <path d="M3 6.2 L5 8.2 L9 3.8" fill="none" stroke="currentColor"
                strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    );
  }

  if (state === 'locked') {
    return (
      <span aria-label="Locked" style={{ ...box, background: 'var(--muted)', color: 'var(--muted-foreground)' }}>
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <rect x="2.8" y="5.4" width="6.4" height="4.6" rx="1.1" fill="currentColor" />
          <path d="M4.3 5.4V4.2a1.7 1.7 0 0 1 3.4 0v1.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
        </svg>
      </span>
    );
  }

  if (state === 'current') {
    return (
      <span aria-label="Current lesson" style={{
        ...box, background: 'var(--card)', color: 'var(--primary)',
        boxShadow: 'inset 0 0 0 2px var(--primary)',
      }}>
        <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--primary)' }} />
      </span>
    );
  }

  return (
    <span style={{ ...box, background: 'var(--muted)', color: 'var(--muted-foreground)' }}>
      {index !== undefined ? index : ''}
    </span>
  );
}
