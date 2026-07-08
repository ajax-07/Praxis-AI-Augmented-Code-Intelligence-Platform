import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { decodeJwt, isExpired } from '../utils/jwt';
import type { PraxisClaims } from '../utils/jwt';

const STORAGE_KEY = 'praxis.token';

interface AuthState {
  token: string | null;
  claims: PraxisClaims | null;
  login: (token: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/** Reject stored tokens that are absent, garbled, or already expired. */
function loadValidToken(): { token: string; claims: PraxisClaims } | null {
  const token = localStorage.getItem(STORAGE_KEY);
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims || isExpired(claims)) {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
  return { token, claims };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(loadValidToken);

  // The api layer needs the token synchronously (interceptor) — keep it fed.
  useEffect(() => {
    setAuthToken(session?.token ?? null);
  }, [session]);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
  }, []);

  const login = useCallback((token: string) => {
    const claims = decodeJwt(token);
    if (!claims) return; // a token we can't read is a token we don't store
    localStorage.setItem(STORAGE_KEY, token);
    setSession({ token, claims });
  }, []);

  // Any 401 on a protected call ends the session in one place.
  useEffect(() => {
    setUnauthorizedHandler(logout);
  }, [logout]);

  const value = useMemo<AuthState>(
    () => ({ token: session?.token ?? null, claims: session?.claims ?? null, login, logout }),
    [session, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
