import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { LogOut, Moon, ScanSearch, Sun } from 'lucide-react';
import { Link as RouterLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useColorMode } from '../../context/ColorModeContext';

/** Top bar + routed content. Only rendered inside ProtectedRoute. */
export default function AppShell() {
  const { claims, logout } = useAuth();
  const { mode, toggle } = useColorMode();

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppBar position="sticky" elevation={0} color="default" sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 2 }}>
          <Box
            component={RouterLink}
            to="/"
            sx={{ display: 'flex', alignItems: 'center', gap: 1, textDecoration: 'none', color: 'inherit' }}
          >
            <ScanSearch size={22} />
            <Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>
              Praxis
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1 }} />
          {claims && <Chip size="small" label={claims.role} variant="outlined" />}
          <Tooltip title={mode === 'dark' ? 'Light mode' : 'Dark mode'}>
            <IconButton onClick={toggle} size="small" aria-label="toggle color mode">
              {mode === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Sign out">
            <IconButton onClick={logout} size="small" aria-label="sign out">
              <LogOut size={18} />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ flexGrow: 1, p: 3, maxWidth: 1400, width: '100%', mx: 'auto' }}>
        <Outlet />
      </Box>
    </Box>
  );
}
