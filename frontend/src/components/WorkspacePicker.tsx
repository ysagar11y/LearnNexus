import { useState } from 'react';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';

import { setTenantSlug } from '@/lib/api';

/**
 * "Which workspace are you signing in to?"
 *
 * The trap this exists to avoid: the field wants a workspace address, but the
 * natural thing to type on a sign-in screen is an email. Stripping the illegal
 * characters silently — turning `priya@acme.test` into `priyaacmetest` — sends
 * the user to a workspace that does not exist with no idea why.
 *
 * So an email is recognised rather than mangled: we explain the difference and
 * offer the domain's first label as a suggestion, which is usually right and is
 * clearly presented as a guess rather than an answer.
 */

export function normaliseSlug(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, '')
    .replace(/\.learnnexus\.app.*$/, '')
    .replace(/[^a-z0-9-]/g, '')
    .slice(0, 63);
}

function looksLikeEmail(value: string): boolean {
  return value.includes('@');
}

/** `priya@acme.test` → `acme`. A guess, and labelled as one. */
function workspaceGuessFromEmail(value: string): string | null {
  const domain = value.split('@')[1];
  if (!domain) return null;
  const label = domain.split('.')[0];
  return label ? normaliseSlug(label) : null;
}

export function WorkspacePicker({
  size = 'lg',
  autoFocus = false,
  suggestions = [],
}: {
  size?: 'md' | 'lg';
  autoFocus?: boolean;
  /** Known workspaces offered as one-click shortcuts. */
  suggestions?: Array<{ slug: string; label: string }>;
}) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [guess, setGuess] = useState<string | null>(null);

  function go(slug: string) {
    setTenantSlug(slug);
    window.location.assign(`/sign-in?tenant=${slug}`);
  }

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const raw = value.trim();
    if (!raw) return;

    if (looksLikeEmail(raw)) {
      const derived = workspaceGuessFromEmail(raw);
      setGuess(derived);
      setError(
        'That is an email address. This box wants your workspace address — the short name in front of .learnnexus.app.',
      );
      return;
    }

    const slug = normaliseSlug(raw);
    if (!slug) {
      setError('Use letters, numbers and hyphens — for example acme.');
      return;
    }
    go(slug);
  }

  // Only meaningful once the entry could actually be an address.
  const preview = !looksLikeEmail(value) ? normaliseSlug(value) : '';

  return (
    <div>
      <form
        className="row row-gap-2"
        style={{ justifyContent: 'center', flexWrap: 'wrap' }}
        onSubmit={onSubmit}
      >
        <div style={{ width: 260 }}>
          <Input
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              setError(null);
              setGuess(null);
            }}
            placeholder="acme"
            aria-label="Workspace address"
            autoFocus={autoFocus}
            invalid={!!error}
          />
        </div>
        <Button type="submit" variant={size === 'lg' ? 'accent' : 'primary'} size={size}>
          Continue
        </Button>
      </form>

      {/*
        A live preview rather than a `.learnnexus.app` suffix inside the field.
        The suffix right-aligns to the field's edge, so with a short entry it
        floats far from the text and reads as a second, unrelated thing in the
        box — which is exactly how it was misread. Showing the resulting address
        as it is typed says the same thing without the ambiguity.
      */}
      {preview && !error && (
        <p style={{ marginTop: 10, fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)' }}>
          Takes you to{' '}
          <strong style={{ color: 'var(--foreground)', fontWeight: 'var(--font-weight-medium)' }}>
            {preview}.learnnexus.app
          </strong>
        </p>
      )}

      {error && (
        <p
          role="alert"
          style={{
            marginTop: 10,
            fontSize: 'var(--text-sm)',
            color: 'var(--destructive)',
            maxWidth: '46ch',
            marginInline: 'auto',
          }}
        >
          {error}
        </p>
      )}

      {guess && (
        <p style={{ marginTop: 8, fontSize: 'var(--text-sm)' }}>
          Did you mean{' '}
          <button
            type="button"
            className="link"
            style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit' }}
            onClick={() => go(guess)}
          >
            {guess}.learnnexus.app
          </button>
          ?
        </p>
      )}

      {suggestions.length > 0 && (
        <div
          className="row row-gap-2"
          style={{ justifyContent: 'center', flexWrap: 'wrap', marginTop: 14 }}
        >
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
            Demo workspaces:
          </span>
          {suggestions.map((suggestion) => (
            <button
              key={suggestion.slug}
              type="button"
              className="link"
              style={{
                background: 'none',
                border: 0,
                padding: 0,
                cursor: 'pointer',
                fontSize: 'var(--text-xs)',
              }}
              onClick={() => go(suggestion.slug)}
            >
              {suggestion.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** The seeded workspaces, offered so nobody has to guess an address to look around. */
export const DEMO_WORKSPACES = [
  { slug: 'acme', label: 'Acme Corp' },
  { slug: 'northwind', label: 'Northwind Institute' },
  { slug: 'platform', label: 'Platform console' },
];
