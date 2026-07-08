import type { ProgressEvent as AnalysisProgressEvent } from '../types/api';
import { isTerminal } from '../types/api';

export interface SseSubscription {
  close: () => void;
}

/**
 * Live pipeline progress via SSE. The browser EventSource API cannot set an
 * Authorization header, so the token rides as ?access_token= — the backend's
 * JwtAuthFilter accepts that, but only for /events URIs.
 *
 * The server pushes the current status immediately on subscribe and closes the
 * stream at a terminal status; we also close from our side then, because
 * EventSource would otherwise treat the server's close as an error and
 * reconnect forever. Non-terminal drops are left to the browser's built-in
 * retry (and the polling fallback covers any gap).
 */
export function subscribeToAnalysisEvents(
  analysisId: string,
  token: string,
  onEvent: (event: AnalysisProgressEvent) => void,
): SseSubscription {
  const source = new EventSource(
    `/api/v1/analyses/${analysisId}/events?access_token=${encodeURIComponent(token)}`,
  );

  let sawTerminal = false;

  source.addEventListener('progress', (raw) => {
    try {
      const event = JSON.parse((raw as MessageEvent<string>).data) as AnalysisProgressEvent;
      if (isTerminal(event.status)) {
        sawTerminal = true;
        source.close();
      }
      onEvent(event);
    } catch {
      // A malformed frame is telemetry noise, never worth breaking the page over.
    }
  });

  source.onerror = () => {
    if (sawTerminal) source.close();
  };

  return { close: () => source.close() };
}
