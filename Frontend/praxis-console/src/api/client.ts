import axios, { AxiosError } from 'axios';
import type { ApiErrorBody } from '../types/api';

/**
 * Single Axios instance for the whole app. The JWT and the 401 handler are
 * registered by AuthProvider at startup (module-level setters avoid a circular
 * import between the api layer and React context).
 */
export const http = axios.create({ baseURL: '/api/v1' });

let currentToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  currentToken = token;
}
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
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

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status ?? null;
    // Expired/invalid token on any protected call -> log out once, centrally.
    // Auth endpoints are exempt so a wrong password doesn't "log out" the form.
    if (status === 401 && !error.config?.url?.startsWith('/auth/') && unauthorizedHandler) {
      unauthorizedHandler();
    }
    const body = error.response?.data;
    throw new ApiError(
      body?.code ?? (status ? `HTTP_${status}` : 'NETWORK_ERROR'),
      body?.message ?? (status ? `Request failed (${status})` : 'Cannot reach the Praxis backend'),
      status,
      body?.traceId ?? null,
    );
  },
);
