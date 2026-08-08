import React from 'react';

/**
 * CourseCard — the catalog's atom, and the single most-repeated object
 * in the product. Two modes off one component so a course looks like
 * the same object wherever it appears:
 *
 *   browse    catalog / discovery — level, duration, instructor
 *   enrolled  "my learning" — swaps the meta row for progress + resume
 *
 * The thumbnail falls back to a generated brand-tinted cover keyed off
 * the title, because half the courses in a real tenant never get an
 * image uploaded and a grid of grey rectangles is what makes a catalog
 * look abandoned.
 *
 * Duration renders as "1h 45m", never "105 minutes" — learners budget
 * time in hours, and `estimated_minutes` is stored in minutes.
 */
function fmtDuration(mins) {
  const m = Math.max(0, Math.round(Number(mins) || 0));
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h}h ${rem}m` : `${h}h`;
}

function coverHue(seed = '') {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360;
  return h;
}

const LEVEL_LABEL = { BEGINNER: 'Beginner', INTERMEDIATE: 'Intermediate', ADVANCED: 'Advanced' };

export function CourseCard({
  course = {},
  mode = 'browse',
  onOpen,
  style,
  ...props
}) {
  const {
    title = 'Untitled course',
    summary,
    thumbnailUrl,
    category,
    level = 'BEGINNER',
    estimatedMinutes = 0,
    lessonCount,
    instructor,
    progressPercent = 0,
    isMandatory = false,
    dueAt,
  } = course;

  const [hover, setHover] = React.useState(false);
  const [imgBroken, setImgBroken] = React.useState(false);
  const hue = coverHue(title);
  const done = progressPercent >= 100;
  const showImg = thumbnailUrl && !imgBroken;

  return (
    <article
      onClick={() => onOpen?.(course)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', flexDirection: 'column',
        background: 'var(--card)',
        border: `1px solid ${hover ? 'var(--border-strong)' : 'var(--border)'}`,
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
        boxShadow: hover ? 'var(--shadow-lg)' : 'var(--shadow-sm)',
        transform: hover ? 'translateY(-2px)' : 'translateY(0)',
        transition:
          'box-shadow var(--duration-base) var(--ease-out),' +
          'transform var(--duration-base) var(--ease-out),' +
          'border-color var(--duration-base) var(--ease-out)',
        cursor: onOpen ? 'pointer' : 'default',
        ...style,
      }}
      {...props}
    >
      {/* ---- cover ---- */}
      <div style={{
        position: 'relative', aspectRatio: '16 / 9', overflow: 'hidden',
        background: showImg ? 'var(--muted)' : `linear-gradient(148deg,
          oklch(0.88 0.055 ${hue}) 0%,
          oklch(0.93 0.035 ${(hue + 40) % 360}) 100%)`,
      }}>
        {showImg ? (
          <img
            src={thumbnailUrl} alt=""
            onError={() => setImgBroken(true)}
            style={{
              width: '100%', height: '100%', objectFit: 'cover',
              transform: hover ? 'scale(1.03)' : 'scale(1)',
              transition: 'transform var(--duration-slower) var(--ease-out)',
            }}
          />
        ) : (
          <svg viewBox="0 0 100 56" aria-hidden="true"
               style={{ width: '100%', height: '100%', color: `oklch(0.55 0.09 ${hue})`, opacity: 0.32 }}>
            <path d="M18 40 L38 24 L52 34 L82 12" fill="none" stroke="currentColor"
                  strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
            <circle cx="18" cy="40" r="3.4" fill="currentColor" />
            <circle cx="38" cy="24" r="3.4" fill="currentColor" />
            <circle cx="82" cy="12" r="4.4" fill="currentColor" />
          </svg>
        )}

        {isMandatory && (
          <span style={{
            position: 'absolute', top: 10, insetInlineStart: 10,
            display: 'inline-flex', alignItems: 'center', height: 20,
            paddingInline: 7, borderRadius: 'var(--radius-full)',
            background: 'var(--accent)', color: 'var(--accent-foreground)',
            fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
            letterSpacing: 'var(--tracking-wide)',
          }}>
            Required
          </span>
        )}

        {done && (
          <span aria-label="Completed" style={{
            position: 'absolute', top: 10, insetInlineEnd: 10,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 24, height: 24, borderRadius: 'var(--radius-full)',
            background: 'var(--success)', color: 'var(--success-foreground)', boxShadow: 'var(--shadow-md)',
          }}>
            <svg width="13" height="13" viewBox="0 0 13 13" aria-hidden="true">
              <path d="M3 6.8 L5.4 9.2 L10 4" fill="none" stroke="currentColor"
                    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
        )}
      </div>

      {/* ---- body ---- */}
      <div style={{ display: 'flex', flexDirection: 'column', flex: 1, padding: 'var(--space-5)', gap: 'var(--space-3)' }}>
        {category && (
          <span style={{
            fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
            letterSpacing: 'var(--tracking-caps)', textTransform: 'uppercase',
            color: 'var(--primary)',
          }}>
            {category}
          </span>
        )}

        <h3 style={{
          fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semibold)',
          lineHeight: 'var(--leading-snug)', color: 'var(--foreground)',
          // Two-line clamp keeps every card in the grid the same height
          // without truncating mid-word on the common case.
          display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}>
          {title}
        </h3>

        {summary && mode === 'browse' && (
          <p style={{
            fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)',
            lineHeight: 'var(--leading-normal)',
            display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
          }}>
            {summary}
          </p>
        )}

        <div style={{ marginTop: 'auto', paddingTop: 'var(--space-2)' }}>
          {mode === 'enrolled' ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-xs)' }}>
                <span style={{ color: done ? 'var(--success)' : 'var(--muted-foreground)',
                               fontWeight: 'var(--font-weight-medium)' }}>
                  {done ? 'Completed' : `${progressPercent}% complete`}
                </span>
                {dueAt && !done && (
                  <span style={{ color: 'var(--warning)', fontWeight: 'var(--font-weight-medium)' }}>
                    Due {dueAt}
                  </span>
                )}
              </div>
              <div style={{ height: 5, background: 'var(--track)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                <div style={{
                  height: '100%', width: `${Math.min(100, Math.max(0, progressPercent))}%`,
                  background: done ? 'var(--success)' : 'var(--primary)',
                  borderRadius: 'var(--radius-full)',
                  transition: 'width var(--duration-slower) var(--ease-out)',
                }} />
              </div>
            </div>
          ) : (
            <div style={{
              display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
              fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)', flexWrap: 'wrap',
            }}>
              <span>{LEVEL_LABEL[level] || level}</span>
              <Dot />
              <span>{fmtDuration(estimatedMinutes)}</span>
              {lessonCount !== undefined && (<><Dot /><span>{lessonCount} lessons</span></>)}
              {instructor && (
                <span style={{ marginInlineStart: 'auto', color: 'var(--foreground)',
                               whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '48%' }}>
                  {instructor}
                </span>
              )}
            </div>
          )}
        </div>
      </div>
    </article>
  );
}

function Dot() {
  return <span aria-hidden="true" style={{ width: 2.5, height: 2.5, borderRadius: '50%',
                                           background: 'var(--border-strong)', flexShrink: 0 }} />;
}
