import type {
  AnalysisSummary,
  AnalysisView,
  StartAnalysisRequest,
  StartAnalysisResponse,
} from '../types/api';
import { http } from './client';

export async function startAnalysis(request: StartAnalysisRequest): Promise<StartAnalysisResponse> {
  const { data } = await http.post<StartAnalysisResponse>('/analyses', request);
  return data;
}

/** Multipart upload-and-analyze; onProgress reports 0..100 for the upload bar. */
export async function uploadAnalysis(
  file: File,
  name: string | undefined,
  onProgress: (percent: number) => void,
): Promise<StartAnalysisResponse> {
  const form = new FormData();
  form.append('file', file);
  if (name && name.trim()) form.append('name', name.trim());
  const { data } = await http.post<StartAnalysisResponse>('/analyses/upload', form, {
    onUploadProgress: (e) => {
      if (e.total) onProgress(Math.round((e.loaded / e.total) * 100));
    },
  });
  return data;
}

export async function getAnalysis(id: string): Promise<AnalysisView> {
  const { data } = await http.get<AnalysisView>(`/analyses/${id}`);
  return data;
}

export async function listAnalyses(): Promise<AnalysisSummary[]> {
  const { data } = await http.get<AnalysisSummary[]>('/analyses');
  return data;
}
