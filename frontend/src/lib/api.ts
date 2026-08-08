/**
 * The single HTTP entry point to the LearnNexus API.
 *
 * Two things it owns that nothing else should duplicate:
 *
 * 1. **Tenant addressing.** Every request carries `X-Tenant`. In production each
 *    workspace has its own sub-domain and the server resolves the tenant from
 *    the host; on `localhost` there is no sub-domain, so the slug is read from
 *    `?tenant=` and remembered.
 *
 * 2. **Token rotation.** Access tokens are short-lived and held in memory only.
 *    A 401 triggers exactly one refresh, and concurrent callers share that one
 *    in-flight refresh rather than each starting their own — otherwise a page
 *    with six parallel queries would rotate the refresh token six times and
 *    trip the server's reuse detection, logging the user out.
 */

// Empty in dev (Vite proxies /api to the backend) and whenever the app is
// served from the same origin as the API. Set to an absolute origin at build
// time when the frontend and backend are deployed separately.
const BASE = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`;

const TENANT_KEY = 'ln.tenant';
const REFRESH_KEY = 'ln.refresh';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }

  /** Field-level messages from a `validation_failed` response, if present. */
  get fieldErrors(): Record<string, string> {
    const fields = this.details.fields;
    return fields && typeof fields === 'object' ? (fields as Record<string, string>) : {};
  }
}

// ---------------------------------------------------------------------------
// Tenant
// ---------------------------------------------------------------------------

// Mirrors the backend's TenantResolutionFilter.subdomainOf(): a tenant is a
// single label directly under the configured root domain, never inferred from
// label count. `labels.length > 2` looks equivalent but is not — it also
// matches every host a free platform hands out (learnnexus.pages.dev,
// abc123.onrender.com), which would misread the platform's own subdomain as a
// tenant slug and send every visitor to a workspace that does not exist. With
// no root domain configured (dev, or a deploy with no wildcard DNS) this
// always returns null and the app relies on X-Tenant / ?tenant= instead.
function hostSlug(): string | null {
  const root = import.meta.env.VITE_TENANT_ROOT_DOMAIN;
  if (!root) return null;

  const host = window.location.hostname;
  if (host === root || !host.endsWith(`.${root}`)) return null;

  const candidate = host.slice(0, -(root.length + 1));
  return candidate === '' || candidate.includes('.') ? null : candidate;
}

export function resolveTenantSlug(): string | null {
  const fromQuery = new URLSearchParams(window.location.search).get('tenant');
  if (fromQuery) {
    localStorage.setItem(TENANT_KEY, fromQuery);
    return fromQuery;
  }
  return hostSlug() ?? localStorage.getItem(TENANT_KEY);
}

export function setTenantSlug(slug: string): void {
  localStorage.setItem(TENANT_KEY, slug);
}

export function clearTenantSlug(): void {
  localStorage.removeItem(TENANT_KEY);
}

// ---------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------

/**
 * Access tokens stay in memory. Persisting them would widen the XSS blast radius
 * for no benefit — the refresh token already survives a reload, and it is the
 * one the server can revoke.
 */
let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;

type SessionListener = (signedIn: boolean) => void;
const sessionListeners = new Set<SessionListener>();

export function onSessionChange(listener: SessionListener): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

function announce(signedIn: boolean): void {
  sessionListeners.forEach((listener) => listener(signedIn));
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function hasStoredSession(): boolean {
  return localStorage.getItem(REFRESH_KEY) !== null;
}

export function storeSession(session: { accessToken: string; refreshToken: string }): void {
  accessToken = session.accessToken;
  localStorage.setItem(REFRESH_KEY, session.refreshToken);
  announce(true);
}

export function clearSession(): void {
  accessToken = null;
  localStorage.removeItem(REFRESH_KEY);
  announce(false);
}

async function refreshSession(): Promise<string | null> {
  if (!localStorage.getItem(REFRESH_KEY)) return null;

  // Collapse concurrent refreshes into one request.
  //
  // The token is read *inside* the promise, not before the in-flight check.
  // Reading it earlier opens a race: a second caller can capture the current
  // token, get suspended while the first refresh completes and rotates it, then
  // resume and send the now-stale token. The server correctly treats a replayed
  // refresh token as theft and revokes the whole family — logging the user out.
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = localStorage.getItem(REFRESH_KEY);
      if (!refreshToken) {
        refreshInFlight = null;
        return null;
      }
      try {
        const response = await fetch(`${BASE}/auth/refresh`, {
          method: 'POST',
          headers: jsonHeaders(),
          body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) {
          clearSession();
          return null;
        }
        const session = await response.json();
        storeSession(session);
        return session.accessToken as string;
      } catch {
        clearSession();
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

function jsonHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  headers.set('Content-Type', 'application/json');
  const tenant = resolveTenantSlug();
  if (tenant) headers.set('X-Tenant', tenant);
  return headers;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Skips the bearer token and the refresh dance — used by sign-in and verification. */
  anonymous?: boolean;
  signal?: AbortSignal;
  query?: Record<string, string | number | boolean | null | undefined>;
}

function withQuery(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

async function execute(path: string, options: RequestOptions, retry: boolean): Promise<Response> {
  const headers = jsonHeaders();
  if (!options.anonymous && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${BASE}${withQuery(path, options.query)}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });

  if (response.status === 401 && !options.anonymous && retry) {
    const renewed = await refreshSession();
    if (renewed) {
      return execute(path, options, false);
    }
  }
  return response;
}

async function toError(response: Response): Promise<ApiError> {
  let code = 'request_failed';
  let message = `Request failed with status ${response.status}.`;
  let details: Record<string, unknown> = {};
  try {
    const body = await response.json();
    code = body.code ?? code;
    message = body.message ?? message;
    details = body.details ?? {};
  } catch {
    /* Non-JSON error body — keep the generic message. */
  }
  return new ApiError(response.status, code, message, details);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await execute(path, options, true);

  if (!response.ok) {
    if (response.status === 401 && !options.anonymous) {
      clearSession();
    }
    throw await toError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query']) => request<T>(path, { query }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),

  /** Anonymous GET, for the sign-in screen and public certificate verification. */
  publicGet: <T>(path: string) => request<T>(path, { anonymous: true }),
  publicPost: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body, anonymous: true }),
};

/**
 * Downloads a file through the authenticated API and hands it to the browser.
 * Used for CSV report exports and certificate PDFs, which cannot be plain links
 * because they need an Authorization header.
 */
export async function download(path: string, filename: string): Promise<void> {
  const response = await execute(path, {}, true);
  if (!response.ok) {
    throw await toError(response);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoking immediately can cancel the download in Safari.
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

/** Restores a session on page load, if a refresh token survived. */
export async function bootstrapSession(): Promise<boolean> {
  if (!hasStoredSession()) return false;
  return (await refreshSession()) !== null;
}
