import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { FolderSearch, Plus } from 'lucide-react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import EmptyState from '../components/common/EmptyState';
import QueryError from '../components/common/QueryError';
import { ListSkeleton } from '../components/common/Skeletons';
import { useAnalyses } from '../hooks/useAnalyses';
import type { AnalysisStatus } from '../types/api';
import { formatDateTime, shortId } from '../utils/format';
import { scoreColor } from '../utils/severity';

function StatusChip({ status }: { status: AnalysisStatus }) {
  const color = status === 'COMPLETE' ? 'success' : status === 'FAILED' ? 'error' : 'info';
  return <Chip size="small" color={color} variant={color === 'info' ? 'outlined' : 'filled'} label={status} />;
}

export default function AnalysesListPage() {
  const navigate = useNavigate();
  const { data, isPending, isError, error, refetch } = useAnalyses();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          Analyses
        </Typography>
        <Button component={RouterLink} to="/new" variant="contained" startIcon={<Plus size={16} />}>
          New analysis
        </Button>
      </Box>

      {isPending && <ListSkeleton />}
      {isError && <QueryError error={error} onRetry={() => void refetch()} />}
      {data && data.length === 0 && (
        <EmptyState
          icon={<FolderSearch size={40} />}
          title="No analyses yet"
          subtitle="Point Praxis at a GitHub repo or upload a zip to get your first health score."
          action={
            <Button component={RouterLink} to="/new" variant="outlined" startIcon={<Plus size={14} />}>
              Start one
            </Button>
          }
        />
      )}
      {data && data.length > 0 && (
        <Paper variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Analysis</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="center">Health</TableCell>
                <TableCell>Started</TableCell>
                <TableCell>Completed</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((a) => (
                <TableRow
                  key={a.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/analyses/${a.id}`)}
                >
                  <TableCell sx={{ fontFamily: 'monospace' }}>{shortId(a.id)}</TableCell>
                  <TableCell>
                    <StatusChip status={a.status} />
                  </TableCell>
                  <TableCell align="center">
                    {a.healthScore != null ? (
                      <Typography sx={{ fontWeight: 700, color: scoreColor(a.healthScore) }}>
                        {a.healthScore}
                      </Typography>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>{formatDateTime(a.createdAt)}</TableCell>
                  <TableCell>{formatDateTime(a.completedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  );
}
