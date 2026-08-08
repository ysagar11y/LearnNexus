import React from 'react';

/**
 * DropdownMenu — the row-actions and account menu.
 *
 * Closes on outside click and on Escape, supports arrow-key navigation,
 * and flips above the trigger when there is not enough room below. That
 * last one matters on admin tables: the actions menu on the final row is
 * otherwise permanently clipped by the viewport.
 */
export function DropdownMenu({
  trigger,
  items = [],
  align = 'end',
  onSelect,
  style,
  ...props
}) {
  const [open, setOpen] = React.useState(false);
  const [flip, setFlip] = React.useState(false);
  const [cursor, setCursor] = React.useState(-1);
  const rootRef = React.useRef(null);
  const menuRef = React.useRef(null);

  const selectable = items.filter((i) => !i.separator && !i.disabled);

  React.useEffect(() => {
    if (!open) { setCursor(-1); return; }

    function onDocDown(e) {
      if (!rootRef.current?.contains(e.target)) setOpen(false);
    }
    function onKey(e) {
      if (e.key === 'Escape') { setOpen(false); return; }
      if (!['ArrowDown', 'ArrowUp', 'Enter'].includes(e.key)) return;
      e.preventDefault();
      if (e.key === 'Enter') {
        const item = selectable[cursor];
        if (item) { onSelect?.(item); item.onSelect?.(); setOpen(false); }
        return;
      }
      setCursor((c) => {
        const n = selectable.length;
        if (!n) return -1;
        return e.key === 'ArrowDown' ? (c + 1) % n : (c - 1 + n) % n;
      });
    }

    // Flip when the menu would overflow the viewport bottom.
    const rect = rootRef.current?.getBoundingClientRect();
    if (rect) setFlip(window.innerHeight - rect.bottom < 240);

    document.addEventListener('mousedown', onDocDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, cursor, selectable, onSelect]);

  return (
    <div ref={rootRef} style={{ position: 'relative', display: 'inline-flex', ...style }} {...props}>
      <span onClick={() => setOpen((o) => !o)} style={{ display: 'inline-flex' }}>
        {typeof trigger === 'function' ? trigger({ open }) : trigger}
      </span>

      {open && (
        <div
          ref={menuRef}
          role="menu"
          style={{
            position: 'absolute',
            [flip ? 'bottom' : 'top']: 'calc(100% + 6px)',
            [align === 'end' ? 'right' : 'left']: 0,
            zIndex: 'var(--z-dropdown)',
            minWidth: 190,
            padding: 'var(--space-1)',
            background: 'var(--popover)', color: 'var(--popover-foreground)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)',
            boxShadow: 'var(--shadow-lg)',
            animation: 'ln-menu var(--duration-fast) var(--ease-out)',
          }}
        >
          {items.map((item, i) => {
            if (item.separator) {
              return <div key={`sep-${i}`} aria-hidden="true"
                          style={{ height: 1, background: 'var(--border)', margin: 'var(--space-1) 0' }} />;
            }
            const si = selectable.indexOf(item);
            const focused = si === cursor;
            const danger = item.tone === 'danger';

            return (
              <button
                key={item.key ?? item.label}
                role="menuitem"
                disabled={item.disabled}
                onMouseEnter={() => setCursor(si)}
                onClick={() => { onSelect?.(item); item.onSelect?.(); setOpen(false); }}
                style={{
                  display: 'flex', alignItems: 'center', gap: 9, width: '100%',
                  minHeight: 32, padding: '6px 9px',
                  border: 'none', borderRadius: 'var(--radius-sm)',
                  background: focused ? (danger ? 'var(--danger-soft)' : 'var(--muted)') : 'transparent',
                  color: danger ? 'var(--destructive)' : 'var(--foreground)',
                  fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
                  textAlign: 'start', cursor: item.disabled ? 'not-allowed' : 'pointer',
                  opacity: item.disabled ? 0.45 : 1,
                }}
              >
                {item.icon && <span aria-hidden="true" style={{ display: 'flex', opacity: 0.8 }}>{item.icon}</span>}
                <span style={{ flex: 1 }}>{item.label}</span>
                {item.shortcut && (
                  <kbd style={{
                    fontFamily: 'var(--font-mono)', fontSize: 'var(--text-2xs)',
                    color: 'var(--muted-foreground)',
                  }}>
                    {item.shortcut}
                  </kbd>
                )}
              </button>
            );
          })}
          <style>{'@keyframes ln-menu{from{opacity:0;transform:translateY(-3px)}to{opacity:1;transform:none}}'}</style>
        </div>
      )}
    </div>
  );
}
