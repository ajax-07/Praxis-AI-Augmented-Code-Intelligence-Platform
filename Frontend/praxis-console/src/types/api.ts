/**
 * 1:1 mirror of the backend contract (com.praxis.* api DTOs).
 * If a backend record changes, this file is the single place to update.
 */

export type SourceType = 'GITHUB' | 'ZIP';

export type AnalysisStatus =
  | 'QUEUED'
  | 'FETCHING'
  | 'PARSING'
  | 'ANALYZING'
  | 'SUMMARIZING'
  | 'SCORING'
  | 'COMPLETE'
  | 'FAILED';

export type Severity = 'INFO' | 'MINOR' | 'MAJOR' | 'CRITICAL';
export type FindingSource = 'STATIC' | 'AI';

// ---- auth ----
export interface RegisterRequest {
  email: string;
  password: string;
  tenantName: string;
}
export interface LoginRequest {
  email: string;
  password: string;
}
export interface AuthResponse {
  token: string;
  tokenType: string;
}

// ---- analyses (conductor) ----
export interface StartAnalysisRequest {
  name: string;
  sourceType: SourceType;
  sourceRef: string;
}
export interface StartAnalysisResponse {
  analysisId: string;
  status: AnalysisStatus;
}
export interface AnalysisView {
  id: string;
  repositoryId: string;
  status: AnalysisStatus;
  healthScore: number | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
}
export interface AnalysisSummary {
  id: string;
  repositoryId: string;
  status: AnalysisStatus;
  healthScore: number | null;
  createdAt: string;
  completedAt: string | null;
}
export interface ProgressEvent {
  analysisId: string;
  status: AnalysisStatus;
  message: string;
  at: string;
}

// ---- dashboard (chronicle) ----
export interface FileSummary {
  fileResultId: string;
  path: string;
  loc: number | null;
  complexity: number | null;
  classCount: number | null;
  findingCount: number;
}
export interface FindingView {
  type: string;
  severity: Severity;
  source: FindingSource;
  message: string;
  suggestion: string | null;
  startLine: number | null;
  endLine: number | null;
}
export interface FileDetail {
  path: string;
  source: string | null;
  findings: FindingView[];
}

// ---- errors ----
export interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string;
}

// ---- helpers shared across the app ----
export const WORKING_STAGES: readonly AnalysisStatus[] = [
  'QUEUED',
  'FETCHING',
  'PARSING',
  'ANALYZING',
  'SUMMARIZING',
  'SCORING',
] as const;

export function isTerminal(status: AnalysisStatus): boolean {
  return status === 'COMPLETE' || status === 'FAILED';
}
