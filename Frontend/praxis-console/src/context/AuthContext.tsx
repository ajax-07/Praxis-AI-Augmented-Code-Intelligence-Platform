import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { refreshTokens } from '../api/auth.api';
import { setAuthToken, setRefreshHandler, setUnauthorizedHandler } from '../api/client';
import { decodeJwt, isExpired } from '../utils/jwt';
import type { PraxisClaims } from '../utils/jwt';

const ACCESS_KEY = 'praxis.accessToken';
const REFRESH_KEY = 'praxis.refreshToken';

interface Session {
  accessToken: string;
  refreshToken: string;
  claims: PraxisClaims | null;
}

interface AuthState {
  token: string | null; // access token — used by the SSE query-param auth
  claims: PraxisClaims | null;
  login: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function clearStorage(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

/**
 * Restore a session on boot. We keep the user logged in as long as the REFRESH
 * token is still alive (7 days) — an expired access token is fine, the API
 * layer refreshes it on the first request. Only a dead/absent refresh token
 * forces a real re-login.
 */
function loadSession(): Session | null {
  const accessToken = localStorage.getItem(ACCESS_KEY);
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!accessToken || !refreshToken) {
    clearStorage();
    return null;
  }
  const refreshClaims = decodeJwt(refreshToken);
  if (!refreshClaims || isExpired(refreshClaims)) {
    clearStorage();
    return null;
  }
  return { accessToken, refreshToken, claims: decodeJwt(accessToken) };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(loadSession);

  const login = useCallback((accessToken: string, refreshToken: string) => {
    const claims = decodeJwt(accessToken);
    if (!claims) return; // a token we can't read is a token we don't store
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    setAuthToken(accessToken); // feed the api layer immediately (don't wait for the effect)
    setSession({ accessToken, refreshToken, claims });
  }, []);

  const logout = useCallback(() => {
    clearStorage();
    setAuthToken(null);
    setSession(null);
  }, []);

  // Keep the api layer's access token in sync with our session.
  useEffect(() => {
    setAuthToken(session?.accessToken ?? null);
  }, [session]);

  // Wire the api layer's central handlers: 401-after-failed-refresh → logout,
  // and the actual refresh call (reads the latest refresh token from storage).
  useEffect(() => {
    setUnauthorizedHandler(logout);
    setRefreshHandler(async () => {
      const stored = localStorage.getItem(REFRESH_KEY);
      if (!stored) return null;
      try {
        const res = await refreshTokens(stored);
        login(res.accessToken, res.refreshToken);
        return res.accessToken;
      } catch {
        return null; // caller logs out
      }
    });
    return () => setRefreshHandler(null);
  }, [login, logout]);

  const value = useMemo<AuthState>(
    () => ({ token: session?.accessToken ?? null, claims: session?.claims ?? null, login, logout }),
    [session, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
