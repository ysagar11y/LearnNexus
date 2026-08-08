/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Root domain tenant sub-domains resolve under, e.g. "learnnexus.app". Unset in dev and on hosts with no wildcard DNS — the app falls back to the X-Tenant header / ?tenant= query param. */
  readonly VITE_TENANT_ROOT_DOMAIN?: string;
  /** Absolute origin of the API when it is not the same origin as the app, e.g. "https://learnnexus-api.onrender.com". Empty in dev, where Vite's proxy makes /api relative. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
