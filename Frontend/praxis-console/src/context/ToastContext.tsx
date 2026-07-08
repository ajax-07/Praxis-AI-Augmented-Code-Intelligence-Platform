import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

type ToastSeverity = 'success' | 'info' | 'warning' | 'error';

interface Toast {
  key: number;
  severity: ToastSeverity;
  message: string;
}

interface ToastState {
  showToast: (severity: ToastSeverity, message: string) => void;
}

const ToastContext = createContext<ToastState | null>(null);

/** One global snackbar, FIFO queue — every mutation error/success lands here. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<Toast[]>([]);
  const current = queue[0];

  const showToast = useCallback((severity: ToastSeverity, message: string) => {
    setQueue((q) => [...q, { key: Date.now() + Math.random(), severity, message }]);
  }, []);

  const dismiss = useCallback(() => setQueue((q) => q.slice(1)), []);

  const value = useMemo(() => ({ showToast }), [showToast]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <Snackbar
        key={current?.key}
        open={current !== undefined}
        autoHideDuration={5000}
        onClose={(_, reason) => reason !== 'clickaway' && dismiss()}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {current && (
          <Alert severity={current.severity} variant="filled" onClose={dismiss}>
            {current.message}
          </Alert>
        )}
      </Snackbar>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastState {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
