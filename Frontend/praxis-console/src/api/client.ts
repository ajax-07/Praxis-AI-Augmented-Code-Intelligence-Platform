import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorBody } from '../types/api';

/**
 * Single Axios instance for the whole app. The access token, the refresh
 * handler, and the logout handler are registered by AuthProvider at startup
 * (module-level setters avoid a circular import between api and React context).
 */
export const http = axios.create({ baseURL: '/api/v1' });

let currentToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;
// Performs a token refresh and returns the new access token, or null if it fails.
let refreshHandler: (() => Promise<string | null>) | null = null;
// Shared across concurrent 401s so we refresh exactly once (token rotation makes
// a second concurrent refresh use an already-invalidated token → false logout).
let refreshInFlight: Promise<string | null> | null = null;

export function setAuthToken(token: string | null): void {
  currentToken = token;
}
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}
export function setRefreshHandler(handler: (() => Promise<string | null>) | null): void {
  refreshHandler = handler;
}

http.interceptors.request.use((config) => {
  if (currentToken) {
    config.headers.Authorization = `Bearer ${currentToken}`;
  }
  return config;
});

/** Everything downstream can rely on this normalized error shape. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number | null;
  readonly traceId: string | null;

  constructor(code: string, message: string, status: number | null, traceId: string | null) {
    super(message);
    this.code = code;
    this.status = status;
    this.traceId = traceId;
  }
}

function toApiError(error: AxiosError<ApiErrorBody>): ApiError {
  const status = error.response?.status ?? null;
  const body = error.response?.data;
  return new ApiError(
    body?.code ?? (status ? `HTTP_${status}` : 'NETWORK_ERROR'),
    body?.message ?? (status ? `Request failed (${status})` : 'Cannot reach the Praxis backend'),
    status,
    body?.traceId ?? null,
  );
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status ?? null;
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith('/auth/');

    // Access token expired on a protected call: silently refresh once, then
    // replay the original request. Only the refresh *itself* failing logs out —
    // so a normal expiry never bounces the user to the login screen.
    if (status === 401 && original && !isAuthCall && !original._retry && refreshHandler) {
      original._retry = true;
      if (!refreshInFlight) {
        refreshInFlight = refreshHandler().finally(() => {
          refreshInFlight = null;
        });
      }
      const newToken = await refreshInFlight;
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return http(original);
      }
      unauthorizedHandler?.();
    }
    throw toApiError(error);
  },
);
