import { useQuery } from '@tanstack/react-query';
import { getFileDetail, listFiles } from '../api/dashboard.api';

export function useFileTree(analysisId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['files', analysisId],
    queryFn: () => listFiles(analysisId!),
    enabled: !!analysisId && enabled,
    staleTime: Infinity, // results are immutable once an analysis is COMPLETE
  });
}

export function useFileDetail(analysisId: string | undefined, fileResultId: string | null) {
  return useQuery({
    queryKey: ['file', analysisId, fileResultId],
    queryFn: () => getFileDetail(analysisId!, fileResultId!),
    enabled: !!analysisId && !!fileResultId,
    staleTime: Infinity,
  });
}
