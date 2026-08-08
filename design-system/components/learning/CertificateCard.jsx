import React from 'react';

/**
 * CertificateCard — an issued certificate, as shown in a learner's
 * achievements tab and in the verification page.
 *
 * This is the one place the design system is allowed to be openly
 * celebratory. Completing a mandatory compliance course is a genuine
 * moment, and treating the certificate like another grey table row is
 * a wasted retention hook — so it gets the display serif, the warm
 * accent, and a guilloche-style border that reads as "document".
 *
 * Revoked and expired states deliberately strip all of that back: an
 * invalid certificate must never look like a valid one at a glance.
 */
export function CertificateCard({
  certificate = {},
  onDownload,
  onVerify,
  style,
  ...props
}) {
  const {
    courseTitle = 'Course',
    recipientName = '',
    serialNumber,
    issuedAt,
    expiresAt,
    revokedAt,
    score,
  } = certificate;

  const revoked = Boolean(revokedAt);
  const expired = !revoked && expiresAt && new Date(expiresAt) < new Date();
  const invalid = revoked || expired;

  return (
    <div
      style={{
        position: 'relative',
        display: 'flex', flexDirection: 'column', gap: 'var(--space-4)',
        padding: 'var(--space-6)',
        background: invalid ? 'var(--muted)' : 'var(--card)',
        border: `1px solid ${invalid ? 'var(--border)' : 'var(--accent-300)'}`,
        borderRadius: 'var(--radius-lg)',
        boxShadow: invalid ? 'none' : 'var(--shadow-md)',
        overflow: 'hidden',
        ...style,
      }}
      {...props}
    >
      {!invalid && (
        <div aria-hidden="true" style={{
          position: 'absolute', inset: 0, pointerEvents: 'none',
          background:
            'radial-gradient(22rem 12rem at 100% 0%, oklch(from var(--accent) l c h / 0.13), transparent 62%)',
        }} />
      )}

      <div style={{ position: 'relative', display: 'flex', alignItems: 'flex-start', gap: 'var(--space-4)' }}>
        <Seal invalid={invalid} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: 'var(--text-2xs)', fontWeight: 'var(--font-weight-semibold)',
            letterSpacing: 'var(--tracking-caps)', textTransform: 'uppercase',
            color: invalid ? 'var(--muted-foreground)' : 'var(--accent-700)',
          }}>
            {revoked ? 'Revoked' : expired ? 'Expired' : 'Certificate of completion'}
          </div>
          <h3 style={{
            marginTop: 6,
            fontFamily: 'var(--font-display)',
            fontWeight: 'var(--font-weight-normal)',
            fontSize: 'var(--text-xl)',
            lineHeight: 'var(--leading-snug)',
            letterSpacing: 'var(--tracking-tight)',
            color: 'var(--foreground)',
            textDecoration: revoked ? 'line-through' : 'none',
          }}>
            {courseTitle}
          </h3>
          {recipientName && (
            <p style={{ marginTop: 4, fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
              Awarded to <span style={{ color: 'var(--foreground)', fontWeight: 'var(--font-weight-medium)' }}>{recipientName}</span>
            </p>
          )}
        </div>
      </div>

      <div style={{
        position: 'relative',
        display: 'flex', flexWrap: 'wrap', gap: 'var(--space-5)',
        paddingTop: 'var(--space-4)', borderTop: '1px solid var(--border)',
      }}>
        <Field label="Issued" value={issuedAt} />
        {expiresAt && <Field label={expired ? 'Expired' : 'Valid until'} value={expiresAt} tone={expired ? 'danger' : undefined} />}
        {score !== undefined && score !== null && <Field label="Score" value={`${score}%`} />}
        {serialNumber && <Field label="Serial" value={serialNumber} mono />}
      </div>

      {(onDownload || onVerify) && !revoked && (
        <div style={{ position: 'relative', display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
          {onDownload && (
            <button onClick={onDownload} style={btn(true)}>
              <svg width="13" height="13" viewBox="0 0 13 13" aria-hidden="true">
                <path d="M6.5 2v6.4M3.8 6.2 6.5 8.9 9.2 6.2M2.5 10.8h8" fill="none"
                      stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              Download PDF
            </button>
          )}
          {onVerify && <button onClick={onVerify} style={btn(false)}>Verify</button>}
        </div>
      )}
    </div>
  );
}

function btn(primary) {
  return {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    height: 32, paddingInline: 13,
    borderRadius: 'var(--radius-md)',
    border: primary ? '1px solid transparent' : '1px solid var(--input)',
    background: primary ? 'var(--primary)' : 'var(--card)',
    color: primary ? 'var(--primary-foreground)' : 'var(--foreground)',
    fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
    fontWeight: 'var(--font-weight-medium)', cursor: 'pointer',
  };
}

function Field({ label, value, mono, tone }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 'var(--text-2xs)', letterSpacing: 'var(--tracking-wide)',
                    color: 'var(--muted-foreground)', textTransform: 'uppercase' }}>
        {label}
      </div>
      <div style={{
        marginTop: 2, fontSize: 'var(--text-sm)',
        fontFamily: mono ? 'var(--font-mono)' : 'var(--font-sans)',
        fontWeight: 'var(--font-weight-medium)',
        color: tone === 'danger' ? 'var(--destructive)' : 'var(--foreground)',
        whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
      }}>
        {value}
      </div>
    </div>
  );
}

function Seal({ invalid }) {
  return (
    <span aria-hidden="true" style={{
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      width: 46, height: 46, flexShrink: 0,
      color: invalid ? 'var(--muted-foreground)' : 'var(--accent-600)',
      opacity: invalid ? 0.5 : 1,
    }}>
      <svg width="46" height="46" viewBox="0 0 46 46" fill="none">
        <circle cx="23" cy="23" r="17" stroke="currentColor" strokeWidth="1.3" opacity="0.35" />
        <circle cx="23" cy="23" r="13.5" stroke="currentColor" strokeWidth="1" opacity="0.55"
                strokeDasharray="2.5 2.5" />
        <path d="M17.5 23.4 L21 26.9 L28.5 19.4" fill="none" stroke="currentColor"
              strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  );
}
