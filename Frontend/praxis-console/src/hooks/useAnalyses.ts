import { useQuery } from '@tanstack/react-query';
import { listAnalyses } from '../api/analyses.api';

export function useAnalyses() {
  return useQuery({
    queryKey: ['analyses'],
    queryFn: listAnalyses,
    staleTime: 10_000,
  });
}
