import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation } from '@tanstack/react-query';
import { ScanSearch } from 'lucide-react';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { register } from '../api/auth.api';
import { useAuth } from '../context/AuthContext';

/** Registration creates the tenant + its first ADMIN user in one step. */
export default function RegisterPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [tenantName, setTenantName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const mutation = useMutation({
    mutationFn: register,
    onSuccess: (response) => {
      auth.login(response.accessToken, response.refreshToken);
      navigate('/', { replace: true });
    },
  });

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Paper variant="outlined" sx={{ p: 4, width: 380, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <ScanSearch size={26} />
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Create your workspace
          </Typography>
        </Box>
        {mutation.isError && <Alert severity="error">{(mutation.error as Error).message}</Alert>}
        <Box
          component="form"
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate({ email, password, tenantName });
          }}
        >
          <TextField
            label="Workspace name"
            required
            autoFocus
            helperText="Your team or organization"
            value={tenantName}
            onChange={(e) => setTenantName(e.target.value)}
          />
          <TextField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <TextField
            label="Password"
            type="password"
            required
            helperText="At least 8 characters"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="contained" size="large" disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating…' : 'Create workspace'}
          </Button>
        </Box>
        <Typography variant="body2" color="text.secondary">
          Already registered?{' '}
          <RouterLink to="/login" style={{ color: 'inherit' }}>
            Sign in
          </RouterLink>
        </Typography>
      </Paper>
    </Box>
  );
}
