import Chip from '@mui/material/Chip';
import type { Severity } from '../../types/api';
import { SEVERITY_COLOR } from '../../utils/severity';

export default function SeverityChip({ severity }: { severity: Severity }) {
  return (
    <Chip
      size="small"
      label={severity}
      sx={{
        fontWeight: 600,
        fontSize: 11,
        color: '#fff',
        bgcolor: SEVERITY_COLOR[severity],
      }}
    />
  );
}
