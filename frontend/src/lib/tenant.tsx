import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, resolveTenantSlug } from './api';
import type { TenantPublic } from './types';

/**
 * Loads the tenant's public branding and applies it to the document.
 *
 * The whole product re-themes from three numbers — brand hue, brand chroma and
 * accent hue — because every colour token in the design system derives from
 * them. Applying branding is therefore three `setProperty` calls, not a
 * stylesheet swap, and it costs nothing to do on every navigation.
 */

interface TenantContextValue {
  tenant: TenantPublic | null;
  slug: string | null;
  loading: boolean;
  /** True when the address resolves to no known workspace. */
  unknown: boolean;
  reload: () => void;
}

const TenantContext = createContext<TenantContextValue | null>(null);

const BRAND_CACHE_KEY = 'ln.brand';

export function applyBranding(brandHue: number, brandChroma: number, accentHue: number): void {
  const root = document.documentElement.style;
  root.setProperty('--brand-h', String(brandHue));
  root.setProperty('--brand-c', String(brandChroma));
  root.setProperty('--accent-h', String(accentHue));

  // Cached so the next page load paints the right palette before React mounts.
  localStorage.setItem(
    BRAND_CACHE_KEY,
    JSON.stringify({ h: brandHue, c: brandChroma, a: accentHue }),
  );
}

function applyFavicon(url?: string | null): void {
  if (!url) return;
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    document.head.appendChild(link);
  }
  link.href = url;
}

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenant, setTenant] = useState<TenantPublic | null>(null);
  const [loading, setLoading] = useState(true);
  const [unknown, setUnknown] = useState(false);
  const [nonce, setNonce] = useState(0);

  const slug = resolveTenantSlug();

  useEffect(() => {
    let cancelled = false;

    if (!slug) {
      setLoading(false);
      setTenant(null);
      setUnknown(false);
      return;
    }

    setLoading(true);
    api
      .publicGet<TenantPublic>('/public/tenant')
      .then((loaded) => {
        if (cancelled) return;
        setTenant(loaded);
        setUnknown(false);
        applyBranding(loaded.brandHue, loaded.brandChroma, loaded.accentHue);
        applyFavicon(loaded.faviconUrl);
        document.title = `${loaded.name} · LearnNexus`;
      })
      .catch(() => {
        if (cancelled) return;
        setTenant(null);
        setUnknown(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [slug, nonce]);

  const value = useMemo(
    () => ({ tenant, slug, loading, unknown, reload: () => setNonce((n) => n + 1) }),
    [tenant, slug, loading, unknown],
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenant(): TenantContextValue {
  const context = useContext(TenantContext);
  if (!context) throw new Error('useTenant must be used inside TenantProvider');
  return context;
}
