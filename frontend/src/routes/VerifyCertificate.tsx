import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge } from '@ds/components/feedback/Badge';

import { api } from '@/lib/api';
import { formatDate } from '@/lib/format';
import type { VerificationResult } from '@/lib/types';
import { IconCertificate, IconCheck, IconClock, IconClose, IconLogo } from '@/components/icons';

/**
 * Public certificate verification.
 *
 * Reached by a recruiter or auditor holding a printed certificate, so it must
 * work with no account, no workspace and no branding. It deliberately shows only
 * what confirms the credential — never the learner's email, workspace or any
 * other record.
 */
export function VerifyCertificate() {
  const { code } = useParams<{ code: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['verify', code],
    queryFn: () => api.publicGet<VerificationResult>(`/verify/${code}`),
    retry: false,
  });

  return (
    <div
      className="ln-wash"
      style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', padding: '32px 20px' }}
    >
      <main style={{ width: '100%', maxWidth: 520 }}>
        <div className="row row-gap-2" style={{ justifyContent: 'center', marginBottom: 22 }}>
          <IconLogo size={22} style={{ color: 'var(--primary)' }} />
          <span style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--font-weight-semibold)' }}>
            LearnNexus
          </span>
        </div>

        <div className="surface" style={{ padding: 30, textAlign: 'center' }}>
          {isLoading ? (
            <p style={{ color: 'var(--muted-foreground)' }}>Checking this certificate…</p>
          ) : isError || !data ? (
            <>
              <StatusMark tone="danger" icon={<IconClose size={24} />} />
              <h1 style={{ fontSize: 'var(--text-xl)', marginBottom: 8 }}>Not found</h1>
              <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
                No certificate matches the code <strong>{code}</strong>. Check for typos — the code is
                printed at the bottom of the certificate.
              </p>
            </>
          ) : (
            <>
              <StatusMark
                tone={data.status === 'VALID' ? 'success' : data.status === 'EXPIRED' ? 'warning' : 'danger'}
                icon={
                  data.status === 'VALID' ? (
                    <IconCheck size={24} />
                  ) : data.status === 'EXPIRED' ? (
                    <IconClock size={24} />
                  ) : (
                    <IconClose size={24} />
                  )
                }
              />

              <div style={{ marginBottom: 10 }}>
                <Badge
                  tone={data.status === 'VALID' ? 'success' : data.status === 'EXPIRED' ? 'warning' : 'danger'}
                  size="md"
                  dot
                >
                  {data.status === 'VALID'
                    ? 'Genuine certificate'
                    : data.status === 'EXPIRED'
                      ? 'Expired'
                      : 'Revoked'}
                </Badge>
              </div>

              <h1
                style={{
                  fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
                  fontWeight: 'var(--font-weight-normal)',
                  fontSize: 'var(--text-2xl)',
                  letterSpacing: 'var(--tracking-display)',
                  marginBottom: 6,
                }}
              >
                {data.recipientName}
              </h1>
              <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 22 }}>
                completed <strong style={{ color: 'var(--foreground)' }}>{data.courseTitle}</strong>
              </p>

              <dl
                className="stack stack-3"
                style={{ textAlign: 'start', fontSize: 'var(--text-sm)', borderTop: '1px solid var(--border)', paddingTop: 18 }}
              >
                {[
                  ['Issued by', data.issuerName],
                  ['Issued on', formatDate(data.issuedAt)],
                  ...(data.expiresAt ? [['Valid until', formatDate(data.expiresAt)]] : []),
                  ['Certificate no.', data.serialNumber],
                ].map(([label, value]) => (
                  <div key={label} className="row" style={{ justifyContent: 'space-between', gap: 16 }}>
                    <dt className="muted">{label}</dt>
                    <dd style={{ textAlign: 'end' }}>{value}</dd>
                  </div>
                ))}
              </dl>

              {data.status === 'REVOKED' && (
                <p
                  style={{
                    marginTop: 18,
                    fontSize: 'var(--text-xs)',
                    color: 'var(--destructive)',
                    textAlign: 'start',
                  }}
                >
                  The issuing organisation has revoked this certificate. It should not be treated as
                  valid evidence of completion.
                </p>
              )}
            </>
          )}
        </div>

        <p
          style={{
            marginTop: 18,
            textAlign: 'center',
            fontSize: 'var(--text-xs)',
            color: 'var(--muted-foreground)',
          }}
        >
          <IconCertificate size={13} style={{ display: 'inline', verticalAlign: '-2px' }} /> Verified
          directly against the issuing organisation's records.
        </p>
      </main>
    </div>
  );
}

function StatusMark({ tone, icon }: { tone: 'success' | 'warning' | 'danger'; icon: React.ReactNode }) {
  const background =
    tone === 'success' ? 'var(--success-soft)' : tone === 'warning' ? 'var(--warning-soft)' : 'var(--danger-soft)';
  const color =
    tone === 'success' ? 'var(--success)' : tone === 'warning' ? 'var(--warning)' : 'var(--destructive)';

  return (
    <div
      aria-hidden="true"
      style={{
        width: 56,
        height: 56,
        borderRadius: '50%',
        margin: '0 auto 16px',
        display: 'grid',
        placeItems: 'center',
        background,
        color,
      }}
    >
      {icon}
    </div>
  );
}
