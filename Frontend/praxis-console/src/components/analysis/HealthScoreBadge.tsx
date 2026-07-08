import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { scoreColor } from '../../utils/severity';

/** Repository Health Score, 0–100 (verdict's v1 formula). */
export default function HealthScoreBadge({ score, size = 72 }: { score: number; size?: number }) {
  const color = scoreColor(score);
  return (
    <Tooltip title="Repository Health Score (0–100): findings per KLOC + complexity penalty">
      <Box sx={{ position: 'relative', display: 'inline-flex' }}>
        <CircularProgress
          variant="determinate"
          value={score}
          size={size}
          thickness={4.5}
          sx={{ color, '& .MuiCircularProgress-circle': { strokeLinecap: 'round' } }}
        />
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Typography variant="h6" component="span" sx={{ fontWeight: 700, color }}>
            {score}
          </Typography>
        </Box>
      </Box>
    </Tooltip>
  );
}
