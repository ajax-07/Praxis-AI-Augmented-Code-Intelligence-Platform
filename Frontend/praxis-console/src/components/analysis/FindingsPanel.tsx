import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { ChevronDown, ChevronRight, ShieldCheck, Sparkles, Wrench } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { FindingView } from '../../types/api';
import { severityRank } from '../../utils/severity';
import EmptyState from '../common/EmptyState';
import SeverityChip from './SeverityChip';

function FindingCard({ finding, onFocus }: { finding: FindingView; onFocus: () => void }) {
  const [open, setOpen] = useState(false);
  const hasSuggestion = !!finding.suggestion;
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer' }}
        onClick={() => {
          onFocus();
          if (hasSuggestion) setOpen(!open);
        }}
      >
        {hasSuggestion && (open ? <ChevronDown size={14} /> : <ChevronRight size={14} />)}
        <SeverityChip severity={finding.severity} />
        <Chip
          size="small"
          variant="outlined"
          icon={finding.source === 'AI' ? <Sparkles size={12} /> : <Wrench size={12} />}
          label={finding.source === 'AI' ? 'AI' : 'Static'}
        />
        <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }} noWrap>
          {finding.type}
        </Typography>
        {finding.startLine != null && (
          <Typography variant="caption" color="text.secondary">
            L{finding.startLine}
            {finding.endLine != null && finding.endLine !== finding.startLine ? `–${finding.endLine}` : ''}
          </Typography>
        )}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
        {finding.message}
      </Typography>
      {hasSuggestion && (
        <Collapse in={open}>
          <Box
            sx={{
              mt: 1,
              p: 1.5,
              borderRadius: 1,
              bgcolor: 'action.hover',
              whiteSpace: 'pre-wrap',
              fontSize: 13,
            }}
          >
            {finding.suggestion}
          </Box>
        </Collapse>
      )}
    </Paper>
  );
}

/**
 * All findings for the selected file, worst first. An empty AI section is a
 * legitimate state (the funnel selected nothing, or the LLM degraded to
 * static-only) — never an error.
 */
export default function FindingsPanel({
  findings,
  onFocusLine,
}: {
  findings: FindingView[];
  onFocusLine: (line: number | null) => void;
}) {
  const sorted = useMemo(
    () => [...findings].sort((a, b) => severityRank(a.severity) - severityRank(b.severity)),
    [findings],
  );

  if (sorted.length === 0) {
    return (
      <EmptyState
        icon={<ShieldCheck size={40} />}
        title="No findings"
        subtitle="This file passed static analysis clean."
      />
    );
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, overflowY: 'auto', height: '100%', p: 0.5 }}>
      {sorted.map((finding, i) => (
        <FindingCard key={i} finding={finding} onFocus={() => onFocusLine(finding.startLine)} />
      ))}
    </Box>
  );
}
