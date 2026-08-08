import React from 'react';

/**
 * Dialog — modal.
 *
 * Focus is trapped and restored to the trigger on close, Escape closes,
 * and the backdrop is inert to scroll. Those three behaviours are what
 * separate a dialog from a div that looks like one; skipping them is
 * the most common accessibility failure in a design system.
 *
 * On viewports under 640px it docks to the bottom as a sheet — reaching
 * a centred modal's controls one-handed on a phone does not work.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
  style,
  ...props
}) {
  const panelRef = React.useRef(null);
  const restoreRef = React.useRef(null);
  const [mobile, setMobile] = React.useState(
    typeof window !== 'undefined' ? window.innerWidth < 640 : false
  );

  React.useEffect(() => {
    const onResize = () => setMobile(window.innerWidth < 640);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  React.useEffect(() => {
    if (!open) return;

    restoreRef.current = document.activeElement;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusables = () =>
      panelRef.current?.querySelectorAll(
        'a[href],button:not([disabled]),textarea,input,select,[tabindex]:not([tabindex="-1"])'
      ) ?? [];

    // Move focus into the panel on open, or the dialog is announced but
    // the keyboard is still parked behind the backdrop.
    const first = focusables()[0];
    (first || panelRef.current)?.focus?.();

    function onKeyDown(e) {
      if (e.key === 'Escape') { e.stopPropagation(); onClose?.(); return; }
      if (e.key !== 'Tab') return;
      const list = Array.from(focusables());
      if (!list.length) return;
      const firstEl = list[0];
      const lastEl = list[list.length - 1];
      if (e.shiftKey && document.activeElement === firstEl) { e.preventDefault(); lastEl.focus(); }
      else if (!e.shiftKey && document.activeElement === lastEl) { e.preventDefault(); firstEl.focus(); }
    }

    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      document.body.style.overflow = prevOverflow;
      restoreRef.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  const maxWidth = { sm: 380, md: 520, lg: 680, xl: 860 }[size] || 520;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 'var(--z-modal)',
        display: 'flex',
        alignItems: mobile ? 'flex-end' : 'center',
        justifyContent: 'center',
        padding: mobile ? 0 : 'var(--space-6)',
        background: 'var(--backdrop)',
        backdropFilter: 'var(--backdrop-blur)',
        animation: 'ln-fade var(--duration-base) var(--ease-out)',
      }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.(); }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        tabIndex={-1}
        style={{
          width: '100%', maxWidth: mobile ? '100%' : maxWidth,
          maxHeight: mobile ? '88vh' : '86vh',
          display: 'flex', flexDirection: 'column',
          background: 'var(--popover)', color: 'var(--popover-foreground)',
          border: '1px solid var(--border)',
          borderRadius: mobile ? 'var(--radius-xl) var(--radius-xl) 0 0' : 'var(--radius-xl)',
          boxShadow: 'var(--shadow-xl)',
          outline: 'none',
          animation: mobile
            ? 'ln-slide-up var(--duration-slow) var(--ease-out)'
            : 'ln-pop var(--duration-slow) var(--ease-out)',
          ...style,
        }}
        {...props}
      >
        {mobile && (
          <span aria-hidden="true" style={{
            width: 34, height: 4, borderRadius: 'var(--radius-full)',
            background: 'var(--border-strong)', margin: '10px auto 0',
          }} />
        )}

        {(title || description) && (
          <div style={{ padding: 'var(--space-6) var(--space-6) var(--space-4)' }}>
            {title && (
              <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-semibold)' }}>
                {title}
              </h2>
            )}
            {description && (
              <p style={{ marginTop: 6, fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)',
                          lineHeight: 'var(--leading-normal)' }}>
                {description}
              </p>
            )}
          </div>
        )}

        <div style={{ padding: `0 var(--space-6) ${footer ? '0' : 'var(--space-6)'}`, overflowY: 'auto', flex: 1 }}>
          {children}
        </div>

        {footer && (
          <div style={{
            display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)',
            padding: 'var(--space-5) var(--space-6)',
            marginTop: 'var(--space-5)',
            borderTop: '1px solid var(--border)',
            background: 'var(--raised)',
            borderRadius: mobile ? 0 : '0 0 var(--radius-xl) var(--radius-xl)',
          }}>
            {footer}
          </div>
        )}

        <style>{`
          @keyframes ln-fade{from{opacity:0}to{opacity:1}}
          @keyframes ln-pop{from{opacity:0;transform:translateY(6px) scale(.985)}to{opacity:1;transform:none}}
          @keyframes ln-slide-up{from{transform:translateY(100%)}to{transform:none}}
          @media (prefers-reduced-motion: reduce){
            @keyframes ln-pop{from{opacity:1}to{opacity:1}}
            @keyframes ln-slide-up{from{transform:none}to{transform:none}}
          }
        `}</style>
      </div>
    </div>
  );
}
