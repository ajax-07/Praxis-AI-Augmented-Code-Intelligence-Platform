import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev-time proxy: the app always calls a relative /api/... URL, so the same
// build works in dev (proxied to the host-run backend) and in Docker (nginx
// proxies /api to the backend service). No env-specific base URLs in code.
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // Vendor libraries change far less often than app code — splitting them
        // lets returning browsers reuse the cached vendor chunk across deploys.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@emotion/react', '@emotion/styled'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8145', changeOrigin: true },
    },
  },
});
