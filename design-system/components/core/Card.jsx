import React from 'react';

/**
 * Card — the surface primitive.
 *
 * Three elevations, and the rule is that elevation encodes *interaction*,
 * not importance:
 *   flat        static content sitting on the canvas
 *   raised      the default — a discrete object with a hairline + shadow
 *   interactive raised, plus it lifts on hover because it is clickable
 *
 * Making a card "important" by giving it a bigger shadow is how a page
 * ends up with six competing focal points and no hierarchy.
 */
export function Card({
  elevation = 'raised',
  padding = 'md',
  as: Tag = 'div',
  className = '',
  style,
  children,
  ...props
}) {
  const [hover, setHover] = React.useState(false);
  const interactive = elevation === 'interactive';

  const pad = { none: 0, sm: 'var(--space-4)', md: 'var(--space-5)', lg: 'var(--space-6)' }[padding];

  const shadow = elevation === 'flat'
    ? 'none'
    : interactive && hover ? 'var(--shadow-lg)' : 'var(--shadow-sm)';

  return (
    <Tag
      className={className}
      onMouseEnter={interactive ? () => setHover(true) : undefined}
      onMouseLeave={interactive ? () => setHover(false) : undefined}
      style={{
        background: elevation === 'flat' ? 'transparent' : 'var(--card)',
        color: 'var(--card-foreground)',
        border: `1px solid ${interactive && hover ? 'var(--border-strong)' : 'var(--border)'}`,
        borderRadius: 'var(--radius-lg)',
        padding: pad,
        boxShadow: shadow,
        transform: interactive && hover ? 'translateY(-2px)' : 'translateY(0)',
        transition:
          'box-shadow var(--duration-base) var(--ease-out),' +
          'transform var(--duration-base) var(--ease-out),' +
          'border-color var(--duration-base) var(--ease-out)',
        cursor: interactive ? 'pointer' : undefined,
        ...style,
      }}
      {...props}
    >
      {children}
    </Tag>
  );
}

export function CardHeader({ title, subtitle, action, style, ...props }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 'var(--space-4)', ...style }} {...props}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--foreground)' }}>
          {title}
        </h3>
        {subtitle && (
          <p style={{ marginTop: 4, fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
            {subtitle}
          </p>
        )}
      </div>
      {action && <div style={{ flexShrink: 0 }}>{action}</div>}
    </div>
  );
}
