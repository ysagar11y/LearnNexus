import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Select } from '@ds/components/forms/Select';
import { Textarea } from '@ds/components/forms/Textarea';
import { Alert } from '@ds/components/feedback/Alert';
import { Badge } from '@ds/components/feedback/Badge';
import { Progress } from '@ds/components/core/Progress';

import { api } from '@/lib/api';
import { applyBranding, useTenant } from '@/lib/tenant';
import type { Branding } from '@/lib/types';
import { ErrorState, FullPageSpinner, PageHeader } from '@/components/states';
import { IconCertificate, IconDashboard, IconLearning, IconLogo } from '@/components/icons';

/**
 * Tenant theming.
 *
 * The entire product — app, emails and certificate PDFs — derives from three
 * numbers, so this screen is three sliders and a live preview rather than a
 * colour picker per element. Moving a slider repaints the real application
 * immediately, because the preview and the app read the same custom properties.
 */
export default function AdminBranding() {
  const { reload } = useTenant();
  const [form, setForm] = useState<Branding | null>(null);
  const [saved, setSaved] = useState(false);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['branding'],
    queryFn: () => api.get<Branding>('/workspace/branding'),
  });

  useEffect(() => {
    if (data && !form) setForm(data);
  }, [data, form]);

  // Repaint live so the sliders act on the product, not on a mock-up.
  useEffect(() => {
    if (form) applyBranding(form.brandHue, form.brandChroma, form.accentHue);
  }, [form?.brandHue, form?.brandChroma, form?.accentHue]);

  // Leaving without saving must not strand the workspace on an unsaved palette.
  useEffect(
    () => () => {
      if (data) applyBranding(data.brandHue, data.brandChroma, data.accentHue);
    },
    [data],
  );

  const save = useMutation({
    mutationFn: () =>
      api.put<Branding>('/workspace/branding', {
        logoUrl: form!.logoUrl || null,
        logoDarkUrl: form!.logoDarkUrl || null,
        faviconUrl: form!.faviconUrl || null,
        brandHue: form!.brandHue,
        // The wire format carries chroma in thousandths, which keeps the value
        // an integer and avoids floating-point drift between client and server.
        brandChromaMilli: Math.round(form!.brandChroma * 1000),
        accentHue: form!.accentHue,
        defaultTheme: form!.defaultTheme,
        loginHeadline: form!.loginHeadline || null,
        loginSubtext: form!.loginSubtext || null,
        supportEmail: form!.supportEmail || null,
        emailFromName: form!.emailFromName || null,
        emailFooter: form!.emailFooter || null,
        customCss: form!.customCss || null,
      }),
    onSuccess: (updated) => {
      setForm(updated);
      setSaved(true);
      reload();
    },
  });

  if (isLoading || !form) return <FullPageSpinner inline />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;

  const update = <K extends keyof Branding>(key: K, value: Branding[K]) =>
    setForm((current) => (current ? { ...current, [key]: value } : current));

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Branding"
        subtitle="Three numbers re-theme the whole product — app, emails and certificates."
        actions={
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Save branding
          </Button>
        }
      />

      {saved && (
        <div style={{ marginBottom: 18 }}>
          <Alert tone="success" title="Branding saved" onDismiss={() => setSaved(false)}>
            Everyone sees the new palette on their next page load.
          </Alert>
        </div>
      )}

      <div className="columns">
        <div className="stack stack-4">
          <section className="surface" style={{ padding: 22 }}>
            <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 4 }}>Palette</h2>
            <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 20 }}>
              Lightness is fixed at every step, so contrast stays accessible at any hue you pick.
            </p>

            <Dial
              id="brand-hue"
              label="Brand hue"
              hint="0–360"
              value={form.brandHue}
              min={0}
              max={360}
              onChange={(value) => update('brandHue', value)}
              gradient="linear-gradient(to right, oklch(0.72 0.14 0), oklch(0.72 0.14 60), oklch(0.72 0.14 120), oklch(0.72 0.14 180), oklch(0.72 0.14 240), oklch(0.72 0.14 300), oklch(0.72 0.14 360))"
            />

            <Dial
              id="brand-chroma"
              label="Brand chroma"
              hint="Pastel ← → vivid"
              value={Math.round(form.brandChroma * 1000)}
              min={40}
              max={220}
              onChange={(value) => update('brandChroma', value / 1000)}
              display={(value) => (value / 1000).toFixed(3)}
              gradient={`linear-gradient(to right, oklch(0.72 0.02 ${form.brandHue}), oklch(0.72 0.22 ${form.brandHue}))`}
            />

            <Dial
              id="accent-hue"
              label="Accent hue"
              hint="One element per screen"
              value={form.accentHue}
              min={0}
              max={360}
              onChange={(value) => update('accentHue', value)}
              gradient="linear-gradient(to right, oklch(0.78 0.12 0), oklch(0.78 0.12 60), oklch(0.78 0.12 120), oklch(0.78 0.12 180), oklch(0.78 0.12 240), oklch(0.78 0.12 300), oklch(0.78 0.12 360))"
            />

            <div style={{ marginTop: 20 }}>
              <Label>Generated ramp</Label>
              <div style={{ display: 'flex', gap: 3, marginTop: 8 }}>
                {[50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map((step) => (
                  <div
                    key={step}
                    title={`brand-${step}`}
                    style={{
                      flex: 1,
                      height: 34,
                      borderRadius: 'var(--radius-xs)',
                      background: `var(--brand-${step})`,
                    }}
                  />
                ))}
              </div>
            </div>

            <div className="field" style={{ marginTop: 20 }}>
              <Label htmlFor="theme">Default theme</Label>
              <Select
                id="theme"
                value={form.defaultTheme}
                onValueChange={(value) => update('defaultTheme', value as Branding['defaultTheme'])}
                options={[
                  { value: 'SYSTEM', label: 'Follow the device' },
                  { value: 'LIGHT', label: 'Always light' },
                  { value: 'DARK', label: 'Always dark' },
                ]}
              />
            </div>
          </section>

          <section className="surface" style={{ padding: 22 }}>
            <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 16 }}>Identity</h2>

            <div className="field">
              <Label htmlFor="logo" hint="Shown in the sidebar and on emails">
                Logo URL
              </Label>
              <Input
                id="logo"
                value={form.logoUrl ?? ''}
                onChange={(event) => update('logoUrl', event.target.value)}
                placeholder="https://cdn.acme.com/logo.svg"
              />
            </div>

            <div className="field">
              <Label htmlFor="favicon">Favicon URL</Label>
              <Input
                id="favicon"
                value={form.faviconUrl ?? ''}
                onChange={(event) => update('faviconUrl', event.target.value)}
              />
            </div>

            <div className="field">
              <Label htmlFor="headline" hint="Sign-in screen">
                Headline
              </Label>
              <Input
                id="headline"
                value={form.loginHeadline ?? ''}
                onChange={(event) => update('loginHeadline', event.target.value)}
                placeholder="Build what's next."
              />
            </div>

            <div className="field">
              <Label htmlFor="subtext">Sub-headline</Label>
              <Textarea
                id="subtext"
                rows={2}
                value={form.loginSubtext ?? ''}
                onChange={(event) => update('loginSubtext', event.target.value)}
              />
            </div>

            <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
              <div className="field" style={{ flex: 1 }}>
                <Label htmlFor="support">Support email</Label>
                <Input
                  id="support"
                  type="email"
                  value={form.supportEmail ?? ''}
                  onChange={(event) => update('supportEmail', event.target.value)}
                />
              </div>
              <div className="field" style={{ flex: 1 }}>
                <Label htmlFor="fromName" hint="Email sender">
                  From name
                </Label>
                <Input
                  id="fromName"
                  value={form.emailFromName ?? ''}
                  onChange={(event) => update('emailFromName', event.target.value)}
                />
              </div>
            </div>
          </section>
        </div>

        <aside>
          <div style={{ position: 'sticky', top: 84 }}>
            <div className="ln-eyebrow" style={{ marginBottom: 10 }}>
              Live preview
            </div>
            <ThemePreview form={form} />
            <p style={{ marginTop: 12, fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
              The rest of this page is already using your unsaved palette — that is the preview.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}

function Dial({
  id,
  label,
  hint,
  value,
  min,
  max,
  onChange,
  gradient,
  display,
}: {
  id: string;
  label: string;
  hint: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  gradient: string;
  display?: (value: number) => string;
}) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div className="field-label-row">
        <Label htmlFor={id} hint={hint}>
          {label}
        </Label>
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 'var(--text-xs)',
            color: 'var(--muted-foreground)',
          }}
        >
          {display ? display(value) : value}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        // A range input consumes the wheel in some browsers, so scrolling the
        // page with the pointer over a slider silently re-themes the workspace.
        onWheel={(event) => event.currentTarget.blur()}
        style={{
          width: '100%',
          height: 22,
          appearance: 'none',
          background: gradient,
          borderRadius: 'var(--radius-full)',
          accentColor: 'var(--primary)',
          cursor: 'pointer',
        }}
      />
    </div>
  );
}

/** A miniature of the real shell, so the palette is judged in context. */
function ThemePreview({ form }: { form: Branding }) {
  return (
    <div className="surface" style={{ overflow: 'hidden' }}>
      <div style={{ display: 'flex', minHeight: 300 }}>
        <div
          style={{
            width: 92,
            background: 'var(--sidebar)',
            color: 'var(--sidebar-foreground)',
            padding: 10,
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
          }}
        >
          <div className="row row-gap-2" style={{ marginBottom: 8 }}>
            {form.logoUrl ? (
              <img src={form.logoUrl} alt="" style={{ maxHeight: 16, maxWidth: 60 }} />
            ) : (
              <IconLogo size={15} style={{ color: 'var(--primary)' }} />
            )}
          </div>
          <div
            className="row row-gap-2"
            style={{
              padding: '5px 7px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--sidebar-active)',
              color: 'var(--sidebar-active-foreground)',
              fontSize: 9,
              fontWeight: 600,
            }}
          >
            <IconDashboard size={11} />
            Home
          </div>
          <div className="row row-gap-2" style={{ padding: '5px 7px', fontSize: 9 }}>
            <IconLearning size={11} />
            Learn
          </div>
          <div className="row row-gap-2" style={{ padding: '5px 7px', fontSize: 9 }}>
            <IconCertificate size={11} />
            Certs
          </div>
        </div>

        <div style={{ flex: 1, background: 'var(--background)', padding: 12 }}>
          <div
            style={{
              fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
              fontSize: 17,
              letterSpacing: '-0.02em',
              marginBottom: 10,
            }}
          >
            Good morning.
          </div>

          <div className="surface" style={{ padding: 10, marginBottom: 8, boxShadow: 'none' }}>
            <div style={{ fontSize: 9, color: 'var(--muted-foreground)', marginBottom: 4 }}>
              In progress
            </div>
            <div style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontSize: 20, lineHeight: 1 }}>3</div>
          </div>

          <div className="ln-panel" style={{ padding: 10, marginBottom: 8 }}>
            <div style={{ fontSize: 9.5, fontWeight: 600 }}>Panel surface</div>
            <div style={{ fontSize: 8.5, opacity: 0.8, marginTop: 2 }}>
              The signature pastel block.
            </div>
          </div>

          <div style={{ marginBottom: 8 }}>
            <Progress value={62} size="xs" />
          </div>

          <div className="row row-gap-2 row-wrap" style={{ marginBottom: 10 }}>
            <Badge tone="brand" size="sm">
              Brand
            </Badge>
            <Badge tone="accent" size="sm">
              Required
            </Badge>
            <Badge tone="success" size="sm">
              Passed
            </Badge>
            <Badge tone="danger" size="sm">
              Overdue
            </Badge>
          </div>

          <div className="row row-gap-2">
            <Button size="sm">Primary</Button>
            <Button size="sm" variant="outline">
              Outline
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
