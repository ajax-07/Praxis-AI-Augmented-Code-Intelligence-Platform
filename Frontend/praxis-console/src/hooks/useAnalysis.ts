import { useQuery } from '@tanstack/react-query';
import { getAnalysis } from '../api/analyses.api';
import { isTerminal } from '../types/api';

/**
 * Analysis detail. Polls every 3s while the pipeline is running as a fallback
 * for SSE (a dropped stream must never strand the progress view), and stops
 * polling the moment the status is terminal.
 */
export function useAnalysis(id: string | undefined) {
  return useQuery({
    queryKey: ['analysis', id],
    queryFn: () => getAnalysis(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && isTerminal(status) ? false : 3000;
    },
  });
}
