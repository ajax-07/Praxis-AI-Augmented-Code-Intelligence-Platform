import type { Severity } from '../types/api';

/** One color story for severities, used by chips, code highlights, and counts. */
export const SEVERITY_ORDER: readonly Severity[] = ['CRITICAL', 'MAJOR', 'MINOR', 'INFO'] as const;

export const SEVERITY_COLOR: Record<Severity, string> = {
  CRITICAL: '#d32f2f',
  MAJOR: '#ed6c02',
  MINOR: '#f0b429',
  INFO: '#0288d1',
};

/** Translucent background for highlighting finding lines in the code viewer. */
export const SEVERITY_HIGHLIGHT: Record<Severity, string> = {
  CRITICAL: 'rgba(211, 47, 47, 0.18)',
  MAJOR: 'rgba(237, 108, 2, 0.16)',
  MINOR: 'rgba(240, 180, 41, 0.14)',
  INFO: 'rgba(2, 136, 209, 0.12)',
};

export function severityRank(severity: Severity): number {
  return SEVERITY_ORDER.indexOf(severity);
}

export function scoreColor(score: number): string {
  if (score >= 80) return '#2e7d32';
  if (score >= 60) return '#f0b429';
  if (score >= 40) return '#ed6c02';
  return '#d32f2f';
}
