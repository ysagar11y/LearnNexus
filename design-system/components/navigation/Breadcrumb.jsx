import React from 'react';

/**
 * Breadcrumb — ancestry trail.
 *
 * Matters more here than in most products because the content hierarchy
 * runs four levels deep (Catalog › Course › Module › Lesson) and a
 * learner deep in a lesson otherwise has no way back to the module.
 *
 * Collapses the middle when the trail exceeds `maxItems`, keeping the
 * root and the last two — the two ends are what people actually use.
 */
export function Breadcrumb({ items = [], maxItems = 4, onNavigate, style, ...props }) {
  let shown = items;
  let collapsed = false;

  if (items.length > maxItems) {
    shown = [items[0], null, ...items.slice(-2)];
    collapsed = true;
  }

  return (
    <nav aria-label="Breadcrumb" style={{ minWidth: 0, ...style }} {...props}>
      <ol style={{
        display: 'flex', alignItems: 'center', gap: 6,
        listStyle: 'none', padding: 0, margin: 0,
        fontSize: 'var(--text-sm)', minWidth: 0,
      }}>
        {shown.map((item, i) => {
          const last = i === shown.length - 1;

          if (item === null) {
            return (
              <li key="ellipsis" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ color: 'var(--muted-foreground)' }} title={
                  items.slice(1, -2).map((x) => x.label).join(' › ')
                }>…</span>
                <Chevron />
              </li>
            );
          }

          return (
            <li key={item.href ?? item.label} style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
              {last ? (
                <span aria-current="page" style={{
                  color: 'var(--foreground)', fontWeight: 'var(--font-weight-medium)',
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                }}>
                  {item.label}
                </span>
              ) : (
                <>
                  <a
                    href={item.href ?? '#'}
                    onClick={(e) => { if (onNavigate) { e.preventDefault(); onNavigate(item); } }}
                    style={{
                      color: 'var(--muted-foreground)', textDecoration: 'none',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                      borderRadius: 'var(--radius-xs)',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--foreground)'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--muted-foreground)'; }}
                  >
                    {item.label}
                  </a>
                  <Chevron />
                </>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

function Chevron() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true"
         style={{ color: 'var(--border-strong)', flexShrink: 0 }}>
      <path d="M4.5 2.5 L8 6 L4.5 9.5" fill="none" stroke="currentColor"
            strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
