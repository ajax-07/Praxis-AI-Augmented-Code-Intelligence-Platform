import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

type Mode = 'light' | 'dark';
const STORAGE_KEY = 'praxis.colorMode';

const ColorModeContext = createContext<{ mode: Mode; toggle: () => void } | null>(null);

function buildTheme(mode: Mode) {
  return createTheme({
    palette: {
      mode,
      primary: { main: mode === 'dark' ? '#45b8ca' : '#0e7a8a' },
      background:
        mode === 'dark'
          ? { default: '#10171a', paper: '#172125' }
          : { default: '#f4f6f7', paper: '#ffffff' },
    },
    typography: { fontFamily: '"Segoe UI Variable Text", "Segoe UI", system-ui, sans-serif' },
    shape: { borderRadius: 10 },
  });
}

export function ColorModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<Mode>(() =>
    localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light',
  );
  const theme = useMemo(() => buildTheme(mode), [mode]);
  const value = useMemo(
    () => ({
      mode,
      toggle: () =>
        setMode((m) => {
          const next: Mode = m === 'dark' ? 'light' : 'dark';
          localStorage.setItem(STORAGE_KEY, next);
          return next;
        }),
    }),
    [mode],
  );
  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode() {
  const ctx = useContext(ColorModeContext);
  if (!ctx) throw new Error('useColorMode must be used inside <ColorModeProvider>');
  return ctx;
}
