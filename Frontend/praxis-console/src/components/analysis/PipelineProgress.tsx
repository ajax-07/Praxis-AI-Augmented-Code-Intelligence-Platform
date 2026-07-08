import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Typography from '@mui/material/Typography';
import type { AnalysisStatus } from '../../types/api';
import { WORKING_STAGES } from '../../types/api';

/**
 * The pipeline state machine as a stepper. COMPLETE shows all stages done;
 * FAILED marks the stage that was running when the pipeline died.
 */
export default function PipelineProgress({
  status,
  message,
}: {
  status: AnalysisStatus;
  message: string | null;
}) {
  const failed = status === 'FAILED';
  const activeStep =
    status === 'COMPLETE'
      ? WORKING_STAGES.length
      : Math.max(0, WORKING_STAGES.indexOf(status));

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stepper activeStep={activeStep} alternativeLabel>
        {WORKING_STAGES.map((stage, i) => (
          <Step key={stage}>
            <StepLabel error={failed && i === activeStep}>{stage}</StepLabel>
          </Step>
        ))}
      </Stepper>
      {message && (
        <Box sx={{ mt: 2, textAlign: 'center' }}>
          <Typography variant="body2" color={failed ? 'error' : 'text.secondary'}>
            {message}
          </Typography>
        </Box>
      )}
    </Paper>
  );
}
