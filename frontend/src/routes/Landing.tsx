import { Button } from '@ds/components/forms/Button';

import { DEMO_WORKSPACES, WorkspacePicker } from '@/components/WorkspacePicker';
import {
  IconCertificate,
  IconChart,
  IconLogo,
  IconPalette,
  IconPeople,
  IconShield,
} from '@/components/icons';

/**
 * The marketing surface.
 *
 * Generous density, the display serif on headlines, and the warm accent on the
 * single primary CTA — the three things the design system reserves for this
 * surface and nowhere else.
 */
export function Landing() {
  return (
    <div style={{ background: 'var(--background)', color: 'var(--foreground)' }}>
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 'var(--z-sticky)' as never,
          background: 'var(--background)',
          borderBottom: '1px solid var(--border)',
        }}
      >
        <div className="ln-container row row-gap-3" style={{ height: 64 }}>
          <IconLogo size={24} style={{ color: 'var(--primary)' }} />
          <span style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-semibold)' }}>
            LearnNexus
          </span>
          <div className="spacer" />
          <a href="/sign-in" style={{ textDecoration: 'none' }}>
            <Button variant="ghost" size="sm">
              Sign in
            </Button>
          </a>
        </div>
      </header>

      {/* Hero */}
      <section className="ln-wash">
        <div className="ln-container" style={{ paddingBlock: 'var(--section-y)', textAlign: 'center' }}>
          <p className="ln-eyebrow" style={{ marginBottom: 18 }}>
            Multi-tenant learning platform
          </p>
          <h1 className="ln-display" style={{ maxWidth: '18ch', marginInline: 'auto' }}>
            One platform. A branded portal for every organisation.
          </h1>
          <p
            style={{
              maxWidth: '52ch',
              marginInline: 'auto',
              marginTop: 22,
              fontSize: 'var(--text-md)',
              lineHeight: 'var(--leading-relaxed)',
              color: 'var(--muted-foreground)',
            }}
          >
            Give each customer their own domain, palette, courses and reports — on shared
            infrastructure, with data isolation enforced at the database, not by convention.
          </p>

          <div style={{ marginTop: 32 }}>
            <WorkspacePicker suggestions={DEMO_WORKSPACES} />
          </div>

        </div>
      </section>

      {/* Capabilities */}
      <section className="ln-container" style={{ paddingBlock: 'var(--section-y)' }}>
        <div style={{ textAlign: 'center', marginBottom: 48 }}>
          <h2
            className="ln-display"
            style={{ fontSize: 'var(--text-3xl)', maxWidth: '20ch', marginInline: 'auto' }}
          >
            Everything an enterprise buyer asks for on the first call.
          </h2>
        </div>

        <div className="card-grid">
          {[
            {
              icon: <IconShield size={20} />,
              title: 'Isolation you can demonstrate',
              body: 'Every business table carries a tenant discriminator that the ORM applies automatically. Cross-tenant reads require a deliberate, audited escape hatch.',
            },
            {
              icon: <IconPalette size={20} />,
              title: 'Branding from three numbers',
              body: 'A hue, a chroma and an accent re-theme the app, the emails and the certificate PDFs. No stylesheet rebuild, no per-tenant CSS to maintain.',
            },
            {
              icon: <IconPeople size={20} />,
              title: 'Real organisation hierarchy',
              body: 'Departments nest, managers see only their own subtree, and a course can be assigned to a whole branch of the tree in one action.',
            },
            {
              icon: <IconChart size={20} />,
              title: 'Reports that export',
              body: 'Completion, compliance, assessment scores and learning hours — on screen and as CSV, scoped automatically to what the viewer may see.',
            },
            {
              icon: <IconCertificate size={20} />,
              title: 'Verifiable certificates',
              body: 'Every certificate carries a code anyone can check without an account, and an issuer can revoke without deleting the record.',
            },
            {
              icon: <IconShield size={20} />,
              title: 'An audit trail that cannot be edited',
              body: 'Administrative actions are append-only at the database level, enforced by a trigger rather than by application discipline.',
            },
          ].map((feature) => (
            <article key={feature.title} className="ln-panel">
              <div style={{ marginBottom: 14 }}>{feature.icon}</div>
              <h3 style={{ fontSize: 'var(--text-lg)', marginBottom: 8 }}>{feature.title}</h3>
              <p style={{ fontSize: 'var(--text-sm)', lineHeight: 'var(--leading-relaxed)', opacity: 0.85 }}>
                {feature.body}
              </p>
            </article>
          ))}
        </div>
      </section>

      {/* Roles */}
      <section className="ln-band">
        <div className="ln-container" style={{ paddingBlock: 'var(--section-y)' }}>
          <h2 className="ln-display" style={{ fontSize: 'var(--text-3xl)', marginBottom: 36 }}>
            Six roles, one product.
          </h2>
          <div className="card-grid">
            {[
              ['Learner', 'A dashboard, a player that remembers where they stopped, and a certificate wallet.'],
              ['Instructor', 'Their own courses, a grading queue for written answers, and per-course analytics.'],
              ['Content author', 'A structure editor for sections, lessons and quizzes with a publish workflow.'],
              ['Manager', 'Reporting confined to their own part of the organisation — enforced server-side.'],
              ['Workspace admin', 'People, branding, features, billing limits and the audit trail.'],
              ['Platform admin', 'Provision, resize and suspend workspaces across the whole estate.'],
            ].map(([role, body]) => (
              <div key={role}>
                <h3 style={{ fontSize: 'var(--text-base)', marginBottom: 6 }}>{role}</h3>
                <p style={{ fontSize: 'var(--text-sm)', lineHeight: 'var(--leading-relaxed)', opacity: 0.8 }}>
                  {body}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <footer className="ln-container" style={{ paddingBlock: 40, borderTop: '1px solid var(--border)' }}>
        <div className="row row-gap-3" style={{ flexWrap: 'wrap' }}>
          <IconLogo size={18} style={{ color: 'var(--primary)' }} />
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
            LearnNexus
          </span>
          <div className="spacer" />
          <a href="/sign-in" className="link">
            Sign in
          </a>
        </div>
      </footer>
    </div>
  );
}
