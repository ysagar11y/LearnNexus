import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Badge } from '@ds/components/feedback/Badge';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Alert } from '@ds/components/feedback/Alert';

import { api, download } from '@/lib/api';
import { formatDate } from '@/lib/format';
import type { CertificateView } from '@/lib/types';
import { ErrorState, LoadingCards, PageHeader } from '@/components/states';
import { IconCertificate, IconCheck, IconDownload } from '@/components/icons';

export default function Certificates() {
  const navigate = useNavigate();
  const [copied, setCopied] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['my-certificates'],
    queryFn: () => api.get<CertificateView[]>('/certificates/mine'),
  });

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner">
      <PageHeader
        title="Certificates"
        subtitle="Everything you have earned. Each one carries a code anyone can verify."
      />

      {copied && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="success" title="Link copied" onDismiss={() => setCopied(null)}>
            Share it with anyone who needs to confirm this certificate is genuine.
          </Alert>
        </div>
      )}

      {isLoading ? (
        <LoadingCards count={3} />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<IconCertificate size={24} />}
          title="No certificates yet"
          description="Finish a course that awards one and it will appear here, ready to download and share."
          action={<Button onClick={() => navigate('/my-learning')}>See my courses</Button>}
        />
      ) : (
        <div className="card-grid">
          {data.map((certificate) => {
            const invalid = certificate.revoked || certificate.expired;

            return (
              <article
                key={certificate.id}
                className="surface"
                style={{
                  padding: 20,
                  // A revoked or expired certificate must never look valid at a
                  // glance, so it loses the celebratory treatment entirely.
                  opacity: invalid ? 0.72 : 1,
                  borderColor: invalid ? 'var(--border)' : 'var(--brand-300)',
                }}
              >
                <div className="row row-gap-2" style={{ marginBottom: 14 }}>
                  <div
                    style={{
                      width: 34,
                      height: 34,
                      borderRadius: 'var(--radius-md)',
                      display: 'grid',
                      placeItems: 'center',
                      background: invalid ? 'var(--muted)' : 'var(--primary-soft)',
                      color: invalid ? 'var(--muted-foreground)' : 'var(--primary-soft-foreground)',
                    }}
                  >
                    <IconCertificate size={18} />
                  </div>
                  <div className="spacer" />
                  {certificate.revoked ? (
                    <Badge tone="danger" size="sm" dot>
                      Revoked
                    </Badge>
                  ) : certificate.expired ? (
                    <Badge tone="warning" size="sm" dot>
                      Expired
                    </Badge>
                  ) : (
                    <Badge tone="success" size="sm" dot>
                      Valid
                    </Badge>
                  )}
                </div>

                <h2 style={{ fontSize: 'var(--text-base)', lineHeight: 1.35 }}>
                  {certificate.courseTitle}
                </h2>

                <dl className="stack stack-2" style={{ marginTop: 12, fontSize: 'var(--text-xs)' }}>
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <dt className="muted">Issued</dt>
                    <dd className="numeric">{formatDate(certificate.issuedAt)}</dd>
                  </div>
                  {certificate.expiresAt && (
                    <div className="row" style={{ justifyContent: 'space-between' }}>
                      <dt className="muted">Valid until</dt>
                      <dd className="numeric">{formatDate(certificate.expiresAt)}</dd>
                    </div>
                  )}
                  {certificate.score != null && (
                    <div className="row" style={{ justifyContent: 'space-between' }}>
                      <dt className="muted">Score</dt>
                      <dd className="numeric">{certificate.score}%</dd>
                    </div>
                  )}
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <dt className="muted">Number</dt>
                    <dd style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-2xs)' }}>
                      {certificate.serialNumber}
                    </dd>
                  </div>
                </dl>

                {certificate.revoked && certificate.revokedReason && (
                  <p style={{ marginTop: 12, fontSize: 'var(--text-xs)', color: 'var(--destructive)' }}>
                    {certificate.revokedReason}
                  </p>
                )}

                <div className="row row-gap-2" style={{ marginTop: 16 }}>
                  <Button
                    size="sm"
                    variant={invalid ? 'outline' : 'primary'}
                    onClick={() =>
                      download(`/certificates/${certificate.id}/pdf`, `${certificate.serialNumber}.pdf`)
                    }
                  >
                    <IconDownload size={14} />
                    PDF
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={async () => {
                      await navigator.clipboard.writeText(certificate.verificationUrl);
                      setCopied(certificate.id);
                    }}
                  >
                    {copied === certificate.id ? <IconCheck size={14} /> : null}
                    Copy verify link
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
