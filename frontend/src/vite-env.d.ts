/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin to call in production, e.g. https://ffdash-backend.onrender.com. Unset in local dev. */
  readonly VITE_API_BASE_URL?: string
}
