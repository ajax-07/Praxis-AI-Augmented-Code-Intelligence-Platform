import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation } from '@tanstack/react-query';
import { ScanSearch } from 'lucide-react';
import { useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { login } from '../api/auth.api';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      auth.login(response.accessToken, response.refreshToken);
      navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true });
    },
  });

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Paper variant="outlined" sx={{ p: 4, width: 380, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <ScanSearch size={26} />
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Praxis
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary">
          Sign in to analyze your code.
        </Typography>
        {mutation.isError && <Alert severity="error">{(mutation.error as Error).message}</Alert>}
        <Box
          component="form"
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate({ email, password });
          }}
        >
          <TextField
            label="Email"
            type="email"
            required
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="contained" size="large" disabled={mutation.isPending}>
            {mutation.isPending ? 'Signing in…' : 'Sign in'}
          </Button>
        </Box>
        <Typography variant="body2" color="text.secondary">
          New here?{' '}
          <RouterLink to="/register" style={{ color: 'inherit' }}>
            Create a workspace
          </RouterLink>
        </Typography>
      </Paper>
    </Box>
  );
}
