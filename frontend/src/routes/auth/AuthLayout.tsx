import type { ReactNode } from 'react';
import { useTenant } from '@/lib/tenant';
import { IconLogo } from '@/components/icons';

/**
 * The split sign-in layout: the tenant's brand block on one side, the form on
 * the other. The brand block is a solid pastel panel rather than a photograph —
 * a stock hero image is the fastest way to make a B2B product look generic, and
 * the panel also re-themes correctly for every tenant hue for free.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const { tenant } = useTenant();

  return (
    <div className="auth-layout">
      <aside className="auth-brand">
        <div className="row row-gap-3">
          {tenant?.logoUrl ? (
            <img src={tenant.logoUrl} alt={tenant.name} style={{ maxHeight: 30, maxWidth: 170 }} />
          ) : (
            <>
              <IconLogo size={26} />
              <span style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-semibold)' }}>
                {tenant?.name ?? 'LearnNexus'}
              </span>
            </>
          )}
        </div>

        <div style={{ maxWidth: '30ch' }}>
          <h2
            style={{
              fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
              fontWeight: 'var(--font-weight-normal)',
              fontSize: 'var(--text-3xl)',
              lineHeight: 'var(--leading-tight)',
              letterSpacing: 'var(--tracking-display)',
            }}
          >
            {tenant?.loginHeadline ?? 'Learning that fits how your organisation works.'}
          </h2>
          {tenant?.loginSubtext && (
            <p style={{ marginTop: 14, fontSize: 'var(--text-base)', opacity: 0.82 }}>
              {tenant.loginSubtext}
            </p>
          )}
        </div>

        <p style={{ fontSize: 'var(--text-xs)', opacity: 0.7 }}>
          Powered by LearnNexus
          {tenant?.supportEmail && (
            <>
              {' · '}
              <a href={`mailto:${tenant.supportEmail}`} style={{ color: 'inherit' }}>
                {tenant.supportEmail}
              </a>
            </>
          )}
        </p>
      </aside>

      <main className="auth-form-side">
        <div className="auth-form">
          <div className="row row-gap-2" style={{ marginBottom: 22 }}>
            <IconLogo size={22} style={{ color: 'var(--primary)' }} />
            <span
              style={{
                fontSize: 'var(--text-base)',
                fontWeight: 'var(--font-weight-semibold)',
                letterSpacing: 'var(--tracking-tight)',
              }}
            >
              {tenant?.name ?? 'LearnNexus'}
            </span>
          </div>

          <h1 style={{ fontSize: 'var(--text-xl)', letterSpacing: 'var(--tracking-tight)' }}>
            {title}
          </h1>
          {subtitle && (
            <p style={{ marginTop: 7, fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
              {subtitle}
            </p>
          )}

          <div style={{ marginTop: 22 }}>{children}</div>

          {footer && (
            <div
              style={{
                marginTop: 20,
                fontSize: 'var(--text-sm)',
                color: 'var(--muted-foreground)',
              }}
            >
              {footer}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
