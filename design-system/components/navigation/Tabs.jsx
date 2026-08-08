import React from 'react';

/**
 * Tabs — section switcher.
 *
 * The active indicator is a 2px underline that slides, not a filled pill.
 * Underlines keep the label baseline aligned across states, so the tab
 * row does not shift by a pixel when selection moves — which is exactly
 * the kind of small instability that makes an interface feel cheap.
 *
 * Arrow-key roving focus is implemented because the WAI-ARIA tabs
 * pattern requires it: Tab enters the list, arrows move within it.
 */
export function Tabs({ tabs = [], value, onValueChange, size = 'md', style, ...props }) {
  const refs = React.useRef([]);
  const idx = Math.max(0, tabs.findIndex((t) => t.value === value));

  function onKeyDown(e) {
    if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(e.key)) return;
    e.preventDefault();
    const enabled = tabs.map((t, i) => (t.disabled ? -1 : i)).filter((i) => i >= 0);
    const pos = enabled.indexOf(idx);
    let next;
    if (e.key === 'Home') next = enabled[0];
    else if (e.key === 'End') next = enabled[enabled.length - 1];
    else if (e.key === 'ArrowRight') next = enabled[(pos + 1) % enabled.length];
    else next = enabled[(pos - 1 + enabled.length) % enabled.length];
    onValueChange?.(tabs[next].value);
    refs.current[next]?.focus();
  }

  const pad = size === 'sm' ? '8px 2px' : '11px 2px';
  const fs = size === 'sm' ? 'var(--text-sm)' : 'var(--text-sm)';

  return (
    <div
      role="tablist"
      onKeyDown={onKeyDown}
      style={{
        display: 'flex', gap: 'var(--space-6)',
        borderBottom: '1px solid var(--border)',
        overflowX: 'auto',
        // Hide the horizontal scrollbar on mobile — the tabs themselves
        // are the affordance, a scrollbar under them is visual noise.
        scrollbarWidth: 'none',
        ...style,
      }}
      {...props}
    >
      {tabs.map((t, i) => {
        const selected = t.value === value;
        return (
          <button
            key={t.value}
            ref={(el) => (refs.current[i] = el)}
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            disabled={t.disabled}
            onClick={() => onValueChange?.(t.value)}
            style={{
              position: 'relative',
              display: 'inline-flex', alignItems: 'center', gap: 7,
              padding: pad, border: 'none', background: 'transparent',
              fontFamily: 'var(--font-sans)', fontSize: fs,
              fontWeight: selected ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
              color: selected ? 'var(--foreground)' : 'var(--muted-foreground)',
              cursor: t.disabled ? 'not-allowed' : 'pointer',
              opacity: t.disabled ? 0.45 : 1,
              whiteSpace: 'nowrap',
              transition: 'color var(--duration-fast) var(--ease-out)',
            }}
          >
            {t.label}
            {t.count !== undefined && (
              <span style={{
                fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-medium)',
                padding: '1px 6px', borderRadius: 'var(--radius-full)',
                background: selected ? 'var(--primary-soft)' : 'var(--muted)',
                color: selected ? 'var(--primary-soft-foreground)' : 'var(--muted-foreground)',
                fontVariantNumeric: 'tabular-nums',
              }}>
                {t.count}
              </span>
            )}
            <span aria-hidden="true" style={{
              position: 'absolute', left: 0, right: 0, bottom: -1, height: 2,
              background: selected ? 'var(--primary)' : 'transparent',
              borderRadius: '2px 2px 0 0',
              transition: 'background var(--duration-fast) var(--ease-out)',
            }} />
          </button>
        );
      })}
    </div>
  );
}
