import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Switch } from '@ds/components/forms/Switch';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { Progress } from '@ds/components/core/Progress';

import { ApiError, api } from '@/lib/api';
import { formatBytes, formatDate, formatNumber, humanise } from '@/lib/format';
import type { TenantSettings } from '@/lib/types';
import { ErrorState, FullPageSpinner, PageHeader } from '@/components/states';

const FEATURE_COPY: Record<string, { label: string; description: string }> = {
  assessments: { label: 'Assessments', description: 'Quizzes and exams inside courses.' },
  certificates: { label: 'Certificates', description: 'Award a credential on completion.' },
  discussions: { label: 'Discussions', description: 'Course Q&A between learners and instructors.' },
  live_sessions: { label: 'Live sessions', description: 'Scheduled instructor-led classes.' },
  gamification: { label: 'Gamification', description: 'Badges, streaks and leaderboards.' },
  self_enrollment: { label: 'Self-enrolment', description: 'Let learners join courses themselves.' },
  public_catalog: { label: 'Public catalog', description: 'Show the catalog to everyone in the workspace.' },
  api_access: { label: 'API access', description: 'Issue API keys for server-to-server use.' },
};

export default function AdminSettings() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<TenantSettings>>({});
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, error: loadError, refetch } = useQuery({
    queryKey: ['tenant-settings'],
    queryFn: () => api.get<TenantSettings>('/workspace/settings'),
  });

  useEffect(() => {
    if (data) {
      setForm({
        name: data.name,
        customDomain: data.customDomain ?? '',
        timezone: data.timezone,
        locale: data.locale,
        currency: data.currency,
      });
    }
  }, [data]);

  const save = useMutation({
    mutationFn: () =>
      api.put<TenantSettings>('/workspace/settings', {
        name: form.name,
        customDomain: form.customDomain || null,
        timezone: form.timezone,
        locale: form.locale,
        currency: form.currency,
      }),
    onSuccess: () => {
      setSaved(true);
      queryClient.invalidateQueries({ queryKey: ['tenant-settings'] });
    },
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not save the settings.'),
  });

  const toggleFeature = useMutation({
    mutationFn: ({ feature, enabled }: { feature: string; enabled: boolean }) =>
      api.post('/workspace/features', { feature, enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tenant-settings'] }),
  });

  if (isLoading) return <FullPageSpinner inline />;
  if (loadError) return <ErrorState error={loadError} onRetry={refetch} />;
  if (!data) return null;

  return (
    <div className="app-inner" style={{ maxWidth: 860 }}>
      <PageHeader
        title="Workspace settings"
        subtitle="Addressing, locale and which parts of the product are switched on."
        actions={<Badge status={data.status} size="md" />}
      />

      <section className="surface" style={{ padding: 22, marginBottom: 22 }}>
        <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 16 }}>Plan and usage</h2>

        <div className="row row-gap-3" style={{ marginBottom: 18, flexWrap: 'wrap' }}>
          <Badge tone="brand" size="md">
            {humanise(data.plan)}
          </Badge>
          {data.trialEndsAt && (
            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
              Trial ends {formatDate(data.trialEndsAt)}
            </span>
          )}
        </div>

        <div className="stack stack-4">
          <div>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 'var(--text-sm)' }}>Seats</span>
              <span style={{ fontSize: 'var(--text-sm)' }} className="numeric">
                {formatNumber(data.usage.activeUsers)} of {formatNumber(data.maxUsers)}
              </span>
            </div>
            <Progress
              value={data.usage.seatUtilisationPercent}
              size="sm"
              tone={data.usage.seatUtilisationPercent > 85 ? 'warning' : undefined}
            />
          </div>

          <div>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 'var(--text-sm)' }}>Storage</span>
              <span style={{ fontSize: 'var(--text-sm)' }} className="numeric">
                {formatBytes(data.usage.storageBytes)} of {formatBytes(data.maxStorageBytes)}
              </span>
            </div>
            <Progress
              value={data.usage.storageUtilisationPercent}
              size="sm"
              tone={data.usage.storageUtilisationPercent > 85 ? 'warning' : undefined}
            />
          </div>
        </div>

        <div className="stat-grid" style={{ marginTop: 20 }}>
          {[
            ['Courses', data.usage.courses],
            ['Published', data.usage.publishedCourses],
            ['Enrolments', data.usage.enrollments],
            ['Certificates', data.usage.certificates],
          ].map(([label, value]) => (
            <div key={String(label)}>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>{label}</div>
              <div style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontSize: 'var(--text-xl)' }}>
                {formatNumber(Number(value))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <form
        className="surface"
        style={{ padding: 22, marginBottom: 22 }}
        onSubmit={(event) => {
          event.preventDefault();
          setSaved(false);
          setError(null);
          save.mutate();
        }}
      >
        <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 16 }}>General</h2>

        {saved && (
          <div style={{ marginBottom: 14 }}>
            <Alert tone="success" title="Settings saved" onDismiss={() => setSaved(false)} />
          </div>
        )}
        {error && (
          <div style={{ marginBottom: 14 }}>
            <Alert tone="critical" title="Could not save">
              {error}
            </Alert>
          </div>
        )}

        <div className="field">
          <Label htmlFor="name" required>
            Workspace name
          </Label>
          <Input
            id="name"
            value={form.name ?? ''}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
        </div>

        <div className="field">
          <Label htmlFor="slug" hint="Cannot be changed">
            Address
          </Label>
          <Input id="slug" value={`${data.slug}.learnnexus.app`} disabled readOnly />
        </div>

        <div className="field">
          <Label htmlFor="domain" hint="Point a CNAME at us first">
            Custom domain
          </Label>
          <Input
            id="domain"
            value={form.customDomain ?? ''}
            onChange={(event) => setForm({ ...form, customDomain: event.target.value })}
            placeholder="learn.acme.com"
          />
        </div>

        <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
          <div className="field" style={{ flex: 1 }}>
            <Label htmlFor="timezone">Time zone</Label>
            <Input
              id="timezone"
              value={form.timezone ?? ''}
              onChange={(event) => setForm({ ...form, timezone: event.target.value })}
            />
          </div>
          <div className="field" style={{ flex: 1 }}>
            <Label htmlFor="locale">Locale</Label>
            <Input
              id="locale"
              value={form.locale ?? ''}
              onChange={(event) => setForm({ ...form, locale: event.target.value })}
            />
          </div>
          <div className="field" style={{ width: 110 }}>
            <Label htmlFor="currency">Currency</Label>
            <Input
              id="currency"
              maxLength={3}
              value={form.currency ?? ''}
              onChange={(event) => setForm({ ...form, currency: event.target.value.toUpperCase() })}
            />
          </div>
        </div>

        <Button type="submit" loading={save.isPending}>
          Save settings
        </Button>
      </form>

      <section className="surface" style={{ padding: 22 }}>
        <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 4 }}>Features</h2>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 18 }}>
          Switching a feature off hides it for everyone in this workspace.
        </p>

        <div className="stack stack-4">
          {Object.entries(data.features).map(([feature, enabled]) => {
            const copy = FEATURE_COPY[feature] ?? { label: humanise(feature), description: '' };
            return (
              <Switch
                key={feature}
                checked={enabled}
                onCheckedChange={(next) => toggleFeature.mutate({ feature, enabled: next })}
                label={copy.label}
                description={copy.description}
              />
            );
          })}
        </div>
      </section>
    </div>
  );
}
