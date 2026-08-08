import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { StatTile } from '@ds/components/core/StatTile';
import { Button } from '@ds/components/forms/Button';
import { Badge } from '@ds/components/feedback/Badge';

import { api } from '@/lib/api';
import { formatBytes, formatNumber } from '@/lib/format';
import type { PlatformOverview } from '@/lib/types';
import { ErrorState, FullPageSpinner, PageHeader } from '@/components/states';
import { IconBuilding, IconCertificate, IconCourses, IconPeople } from '@/components/icons';

export default function PlatformOverviewPage() {
  const navigate = useNavigate();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['platform-overview'],
    queryFn: () => api.get<PlatformOverview>('/platform/overview'),
  });

  if (isLoading) return <FullPageSpinner inline />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return null;

  const maxSignups = Math.max(1, ...data.signups.map((point) => point.tenants));

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Platform"
        subtitle="Every workspace on LearnNexus. This is the only place that reads across tenants."
        actions={<Button onClick={() => navigate('/platform/tenants')}>Manage workspaces</Button>}
      />

      <div className="stat-grid">
        <StatTile
          label="Workspaces"
          value={formatNumber(data.tenants)}
          caption={`${data.activeTenants} active · ${data.trialTenants} on trial`}
          icon={<IconBuilding size={15} />}
        />
        <StatTile
          label="Suspended"
          value={formatNumber(data.suspendedTenants)}
          deltaTone="negative"
          caption={data.suspendedTenants ? 'Needs review' : 'None suspended'}
        />
        <StatTile label="Users" value={formatNumber(data.users)} icon={<IconPeople size={15} />} />
        <StatTile label="Courses" value={formatNumber(data.courses)} icon={<IconCourses size={15} />} />
        <StatTile label="Enrolments" value={formatNumber(data.enrolments)} />
        <StatTile
          label="Certificates"
          value={formatNumber(data.certificates)}
          icon={<IconCertificate size={15} />}
        />
      </div>

      <div className="columns section-gap">
        <section className="surface" style={{ padding: 18 }}>
          <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 16 }}>New workspaces</h2>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 160 }}>
            {data.signups.map((point) => (
              <div
                key={point.month}
                style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}
                title={`${point.tenants} in ${new Date(point.month).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })}`}
              >
                <div
                  style={{
                    width: '100%',
                    height: `${(point.tenants / maxSignups) * 120}px`,
                    minHeight: point.tenants > 0 ? 4 : 0,
                    background: 'var(--chart-1)',
                    borderRadius: '3px 3px 0 0',
                  }}
                />
                <span style={{ fontSize: 'var(--text-2xs)', color: 'var(--muted-foreground)' }}>
                  {new Date(point.month).toLocaleDateString('en-GB', { month: 'narrow' })}
                </span>
              </div>
            ))}
          </div>
        </section>

        <aside className="stack stack-4">
          <section className="surface">
            <div className="surface-header">
              <h2>By plan</h2>
            </div>
            {data.plans.map((plan) => (
              <div key={plan.plan} className="surface-row">
                <Badge tone={plan.plan === 'ENTERPRISE' ? 'brand' : 'neutral'} size="sm">
                  {plan.plan}
                </Badge>
                <div className="spacer" />
                <span className="numeric" style={{ fontSize: 'var(--text-sm)' }}>
                  {plan.tenants} {plan.tenants === 1 ? 'workspace' : 'workspaces'}
                </span>
                <span className="muted numeric" style={{ fontSize: 'var(--text-xs)', width: 80, textAlign: 'end' }}>
                  {formatNumber(plan.users)} users
                </span>
              </div>
            ))}
          </section>

          <section className="surface" style={{ padding: 18 }}>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
              Media stored
            </div>
            <div style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontSize: 'var(--text-2xl)', lineHeight: 1.1 }}>
              {formatBytes(data.storageBytes)}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
