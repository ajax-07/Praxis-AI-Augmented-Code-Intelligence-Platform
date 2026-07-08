import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Typography from '@mui/material/Typography';
import { FileCode2 } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import CodeViewer from '../components/analysis/CodeViewer';
import FileTree from '../components/analysis/FileTree';
import FindingsPanel from '../components/analysis/FindingsPanel';
import HealthScoreBadge from '../components/analysis/HealthScoreBadge';
import PipelineProgress from '../components/analysis/PipelineProgress';
import EmptyState from '../components/common/EmptyState';
import QueryError from '../components/common/QueryError';
import { CodeSkeleton, ListSkeleton } from '../components/common/Skeletons';
import { useAnalysis } from '../hooks/useAnalysis';
import { useAnalysisEvents } from '../hooks/useAnalysisEvents';
import { useFileDetail, useFileTree } from '../hooks/useFiles';
import { isTerminal } from '../types/api';
import { shortId } from '../utils/format';

/** Results dashboard: file tree | code | findings. Only rendered when COMPLETE. */
function ResultsDashboard({ analysisId }: { analysisId: string }) {
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
  const [focusLine, setFocusLine] = useState<number | null>(null);
  const files = useFileTree(analysisId, true);
  const detail = useFileDetail(analysisId, selectedFileId);

  if (files.isPending) return <ListSkeleton />;
  if (files.isError) return <QueryError error={files.error} onRetry={() => void files.refetch()} />;

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '260px 1fr 340px' },
        gap: 2,
        height: { md: 'calc(100vh - 240px)' },
        minHeight: 420,
      }}
    >
      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <FileTree
          files={files.data}
          selectedId={selectedFileId}
          onSelect={(f) => {
            setSelectedFileId(f.fileResultId);
            setFocusLine(null);
          }}
        />
      </Paper>
      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        {!selectedFileId && (
          <EmptyState
            icon={<FileCode2 size={40} />}
            title="Pick a file"
            subtitle="Files with badges have findings to review."
          />
        )}
        {selectedFileId && detail.isPending && <CodeSkeleton />}
        {selectedFileId && detail.isError && (
          <Box sx={{ p: 2 }}>
            <QueryError error={detail.error} onRetry={() => void detail.refetch()} />
          </Box>
        )}
        {detail.data && (
          <CodeViewer
            source={detail.data.source ?? '// source unavailable'}
            findings={detail.data.findings}
            focusLine={focusLine}
          />
        )}
      </Paper>
      <Paper variant="outlined" sx={{ overflow: 'hidden', p: 1 }}>
        {detail.data ? (
          <FindingsPanel findings={detail.data.findings} onFocusLine={setFocusLine} />
        ) : (
          <EmptyState icon={<FileCode2 size={32} />} title="Findings" subtitle="Select a file to see its findings." />
        )}
      </Paper>
    </Box>
  );
}

export default function AnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const analysis = useAnalysis(id);
  const running = !!analysis.data && !isTerminal(analysis.data.status);
  const { lastMessage } = useAnalysisEvents(id, running);

  if (analysis.isPending) return <Skeleton variant="rounded" height={160} />;
  if (analysis.isError) return <QueryError error={analysis.error} onRetry={() => void analysis.refetch()} />;

  const view = analysis.data;
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="h5" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
          {shortId(view.id)}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {view.status === 'COMPLETE' && view.healthScore != null && (
          <HealthScoreBadge score={view.healthScore} />
        )}
      </Box>

      {view.status !== 'COMPLETE' && (
        <PipelineProgress status={view.status} message={lastMessage} />
      )}

      {view.status === 'FAILED' && (
        <Alert severity="error">
          <AlertTitle>Analysis failed</AlertTitle>
          {view.errorMessage ?? 'No error detail was recorded.'}
        </Alert>
      )}

      {view.status === 'COMPLETE' && id && <ResultsDashboard analysisId={id} />}
    </Box>
  );
}
