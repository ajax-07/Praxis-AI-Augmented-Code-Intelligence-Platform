import type { FileDetail, FileSummary } from '../types/api';
import { http } from './client';

export async function listFiles(analysisId: string): Promise<FileSummary[]> {
  const { data } = await http.get<FileSummary[]>(`/analyses/${analysisId}/files`);
  return data;
}

export async function getFileDetail(analysisId: string, fileResultId: string): Promise<FileDetail> {
  const { data } = await http.get<FileDetail>(`/analyses/${analysisId}/files/${fileResultId}`);
  return data;
}
