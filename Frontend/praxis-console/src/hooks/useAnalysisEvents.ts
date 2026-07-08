import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { subscribeToAnalysisEvents } from '../api/sse';
import { useAuth } from '../context/AuthContext';
import type { AnalysisView, ProgressEvent } from '../types/api';
import { isTerminal } from '../types/api';

/**
 * Live SSE progress for one analysis. Every event is written straight into
 * the ['analysis', id] query cache so the whole page re-renders from a single
 * source of truth; on a terminal event the result queries are invalidated so
 * the dashboard data loads immediately.
 */
export function useAnalysisEvents(analysisId: string | undefined, enabled: boolean) {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const [lastMessage, setLastMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!analysisId || !token || !enabled) return;

    const subscription = subscribeToAnalysisEvents(analysisId, token, (event: ProgressEvent) => {
      setLastMessage(event.message);
      queryClient.setQueryData<AnalysisView>(['analysis', analysisId], (old) =>
        old ? { ...old, status: event.status } : old,
      );
      if (isTerminal(event.status)) {
        void queryClient.invalidateQueries({ queryKey: ['analysis', analysisId] });
        void queryClient.invalidateQueries({ queryKey: ['files', analysisId] });
        void queryClient.invalidateQueries({ queryKey: ['analyses'] });
      }
    });
    return subscription.close;
  }, [analysisId, token, enabled, queryClient]);

  return { lastMessage };
}
