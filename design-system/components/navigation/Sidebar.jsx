import React from 'react';

/**
 * Sidebar — the app shell rail.
 *
 * Deliberately a tinted brand surface rather than the near-black rail
 * that most admin tools default to. LearnNexus is a learning product
 * used in long sessions; a heavy dark slab down one side raises visual
 * tension and makes the canvas feel like a control panel. The tint keeps
 * the whole window in one colour family while still separating chrome
 * from content.
 *
 * Active item is a raised white pill — the same "lifted" language the
 * cards use, so selection reads as an object rather than a highlight.
 */
export function Sidebar({
  items = [],
  active,
  onSelect,
  collapsed = false,
  header,
  footer,
  style,
  ...props
}) {
  return (
    <nav
      aria-label="Main"
      style={{
        display: 'flex', flexDirection: 'column',
        width: collapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width)',
        flexShrink: 0,
        background: 'var(--sidebar)',
        borderInlineEnd: '1px solid var(--sidebar-border)',
        transition: 'width var(--duration-slow) var(--ease-out)',
        overflow: 'hidden',
        ...style,
      }}
      {...props}
    >
      {header && (
        <div style={{
          height: 'var(--topbar-height)', display: 'flex', alignItems: 'center',
          paddingInline: collapsed ? 0 : 'var(--space-5)',
          justifyContent: collapsed ? 'center' : 'flex-start',
          color: 'var(--sidebar-foreground)', flexShrink: 0,
        }}>
          {header}
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', padding: 'var(--space-3)',
                    display: 'flex', flexDirection: 'column', gap: 2 }}>
        {items.map((item, i) =>
          item.section ? (
            <div key={`s-${i}`} style={{
              padding: collapsed ? '14px 0 6px' : '14px 10px 6px',
              fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
              letterSpacing: 'var(--tracking-caps)', textTransform: 'uppercase',
              color: 'var(--sidebar-muted-foreground)',
              opacity: collapsed ? 0 : 0.85,
              whiteSpace: 'nowrap',
            }}>
              {item.section}
            </div>
          ) : (
            <SidebarItem
              key={item.key ?? item.label}
              item={item}
              collapsed={collapsed}
              active={active === (item.key ?? item.label)}
              onSelect={onSelect}
            />
          )
        )}
      </div>

      {footer && (
        <div style={{ padding: 'var(--space-3)', borderTop: '1px solid var(--sidebar-border)', flexShrink: 0 }}>
          {footer}
        </div>
      )}
    </nav>
  );
}

function SidebarItem({ item, collapsed, active, onSelect }) {
  const [hover, setHover] = React.useState(false);
  const key = item.key ?? item.label;

  return (
    <button
      onClick={() => onSelect?.(key)}
      aria-current={active ? 'page' : undefined}
      title={collapsed ? item.label : undefined}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', alignItems: 'center', gap: 10,
        width: '100%', minHeight: 36,
        padding: collapsed ? '8px 0' : '8px 10px',
        justifyContent: collapsed ? 'center' : 'flex-start',
        border: 'none', borderRadius: 'var(--radius-md)',
        background: active ? 'var(--sidebar-active)' : hover ? 'oklch(from var(--sidebar-active) l c h / 0.55)' : 'transparent',
        boxShadow: active ? 'var(--shadow-sm)' : 'none',
        color: active ? 'var(--sidebar-active-foreground)' : 'var(--sidebar-foreground)',
        fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
        fontWeight: active ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
        cursor: 'pointer', textAlign: 'start',
        transition: 'background var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out)',
      }}
    >
      {item.icon && (
        <span aria-hidden="true" style={{ display: 'flex', flexShrink: 0, opacity: active ? 1 : 0.75 }}>
          {item.icon}
        </span>
      )}
      {!collapsed && (
        <>
          <span style={{ flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {item.label}
          </span>
          {item.badge !== undefined && item.badge !== null && (
            <span style={{
              fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
              minWidth: 18, height: 18, paddingInline: 5,
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              borderRadius: 'var(--radius-full)',
              background: 'var(--primary)', color: 'var(--primary-foreground)',
              fontVariantNumeric: 'tabular-nums',
            }}>
              {item.badge}
            </span>
          )}
        </>
      )}
    </button>
  );
}
