import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Github, UploadCloud } from 'lucide-react';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { useStartAnalysis, useUploadAnalysis } from '../hooks/useStartAnalysis';

function GithubTab() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const start = useStartAnalysis();

  return (
    <Box
      component="form"
      sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
      onSubmit={(e) => {
        e.preventDefault();
        start.mutate(
          { name, sourceType: 'GITHUB', sourceRef: url },
          {
            onSuccess: (r) => navigate(`/analyses/${r.analysisId}`),
            onError: (err) => showToast('error', err.message),
          },
        );
      }}
    >
      <TextField label="Analysis name" required value={name} onChange={(e) => setName(e.target.value)} />
      <TextField
        label="Repository URL"
        required
        placeholder="https://github.com/owner/repo.git"
        helperText="Public repos only (https). The clone is shallow and sandboxed."
        value={url}
        onChange={(e) => setUrl(e.target.value)}
      />
      <Button type="submit" variant="contained" size="large" disabled={start.isPending}>
        {start.isPending ? 'Queuing…' : 'Analyze repository'}
      </Button>
    </Box>
  );
}

function UploadTab() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [name, setName] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadAnalysis();

  const acceptFile = (candidate: File | undefined) => {
    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith('.zip')) {
      showToast('warning', 'Only .zip archives are supported');
      return;
    }
    setFile(candidate);
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <TextField
        label="Analysis name (optional)"
        helperText="Defaults to the file name"
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <Paper
        variant="outlined"
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          acceptFile(e.dataTransfer.files[0]);
        }}
        sx={{
          p: 4,
          textAlign: 'center',
          cursor: 'pointer',
          borderStyle: 'dashed',
          borderColor: dragging ? 'primary.main' : 'divider',
          bgcolor: dragging ? 'action.hover' : undefined,
        }}
      >
        <UploadCloud size={32} style={{ opacity: 0.6 }} />
        <Typography sx={{ mt: 1 }}>
          {file ? file.name : 'Drop a .zip here, or click to browse'}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Java sources, up to 200 MB
        </Typography>
        <input
          ref={inputRef}
          type="file"
          accept=".zip"
          hidden
          onChange={(e) => acceptFile(e.target.files?.[0])}
        />
      </Paper>
      {upload.isPending && <LinearProgress variant="determinate" value={upload.progress} />}
      <Button
        variant="contained"
        size="large"
        disabled={!file || upload.isPending}
        onClick={() =>
          file &&
          upload.mutate(
            { file, name: name || undefined },
            {
              onSuccess: (r) => navigate(`/analyses/${r.analysisId}`),
              onError: (err) => showToast('error', err.message),
            },
          )
        }
      >
        {upload.isPending ? `Uploading ${upload.progress}%` : 'Upload & analyze'}
      </Button>
    </Box>
  );
}

export default function NewAnalysisPage() {
  const [tab, setTab] = useState(0);
  return (
    <Box sx={{ maxWidth: 560, mx: 'auto', display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        New analysis
      </Typography>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Tabs value={tab} onChange={(_, v: number) => setTab(v)} sx={{ mb: 3 }}>
          <Tab icon={<Github size={16} />} iconPosition="start" label="GitHub repo" />
          <Tab icon={<UploadCloud size={16} />} iconPosition="start" label="Upload zip" />
        </Tabs>
        {tab === 0 ? <GithubTab /> : <UploadTab />}
      </Paper>
    </Box>
  );
}
