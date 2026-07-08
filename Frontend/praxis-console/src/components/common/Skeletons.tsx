import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';

export function ListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} variant="rounded" height={44} />
      ))}
    </Box>
  );
}

export function CodeSkeleton() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, p: 2 }}>
      {Array.from({ length: 18 }, (_, i) => (
        <Skeleton key={i} variant="text" width={`${45 + ((i * 37) % 50)}%`} />
      ))}
    </Box>
  );
}
