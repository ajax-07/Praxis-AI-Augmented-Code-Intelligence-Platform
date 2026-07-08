import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import { RefreshCw } from 'lucide-react';
import { ApiError } from '../../api/client';

/** Standard render for a failed query: normalized message + retry. */
export default function QueryError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message =
    error instanceof ApiError
      ? `${error.message}${error.traceId ? ` (trace ${error.traceId})` : ''}`
      : 'Something went wrong';
  return (
    <Alert
      severity="error"
      action={
        onRetry && (
          <Button color="inherit" size="small" startIcon={<RefreshCw size={14} />} onClick={onRetry}>
            Retry
          </Button>
        )
      }
    >
      {message}
    </Alert>
  );
}
