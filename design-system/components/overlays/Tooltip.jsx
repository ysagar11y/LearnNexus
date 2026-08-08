import React from 'react';

/**
 * Tooltip — supplementary hint on hover and on keyboard focus.
 *
 * Focus support is not optional: a tooltip that only appears on hover is
 * invisible to keyboard and touch users, which makes it an unacceptable
 * home for anything load-bearing. Rule for this system — a tooltip may
 * only ever *supplement* a visible label, never replace one. The single
 * exception is an icon-only button, which must also carry an aria-label.
 *
 * Opens after a short delay so sweeping the cursor across a toolbar does
 * not fire a cascade of tooltips.
 */
export function Tooltip({
  content,
  side = 'top',
  delay = 260,
  children,
  style,
  ...props
}) {
  const [open, setOpen] = React.useState(false);
  const timer = React.useRef(null);

  const show = () => { clearTimeout(timer.current); timer.current = setTimeout(() => setOpen(true), delay); };
  const hide = () => { clearTimeout(timer.current); setOpen(false); };

  React.useEffect(() => () => clearTimeout(timer.current), []);

  const pos = {
    top:    { bottom: 'calc(100% + 7px)', left: '50%', transform: 'translateX(-50%)' },
    bottom: { top: 'calc(100% + 7px)',    left: '50%', transform: 'translateX(-50%)' },
    left:   { right: 'calc(100% + 7px)',  top: '50%',  transform: 'translateY(-50%)' },
    right:  { left: 'calc(100% + 7px)',   top: '50%',  transform: 'translateY(-50%)' },
  }[side];

  return (
    <span
      style={{ position: 'relative', display: 'inline-flex', ...style }}
      onMouseEnter={show}
      onMouseLeave={hide}
      onFocus={show}
      onBlur={hide}
      {...props}
    >
      {children}
      {open && content && (
        <span
          role="tooltip"
          style={{
            position: 'absolute', ...pos,
            zIndex: 'var(--z-tooltip)',
            padding: '5px 9px',
            maxWidth: 240, width: 'max-content',
            // Inverted surface: a tooltip has to separate from both the
            // card and the canvas, and inverting is the only treatment
            // that works on every surface level without a per-context token.
            background: 'var(--foreground)',
            color: 'var(--background)',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-xs)',
            lineHeight: 'var(--leading-normal)',
            fontWeight: 'var(--font-weight-medium)',
            boxShadow: 'var(--shadow-md)',
            pointerEvents: 'none',
            animation: 'ln-tip var(--duration-fast) var(--ease-out)',
          }}
        >
          {content}
          <style>{'@keyframes ln-tip{from{opacity:0}to{opacity:1}}'}</style>
        </span>
      )}
    </span>
  );
}
