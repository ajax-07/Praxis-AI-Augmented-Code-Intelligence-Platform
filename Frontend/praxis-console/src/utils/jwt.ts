/** Claims the backend puts in its JWT (identity/internal/JwtService). */
export interface PraxisClaims {
  sub: string; // userId
  tenantId: string;
  user: string
  role: 'ADMIN' | 'MEMBER';
  exp: number; // seconds since epoch
}

/** Decode without verifying — the server is the verifier; we only read claims. */
export function decodeJwt(token: string): PraxisClaims | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as PraxisClaims;
  } catch {
    return null;
  }
}

export function isExpired(claims: PraxisClaims): boolean {
  return claims.exp * 1000 <= Date.now();
}
