import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Same-origin API access: the browser always calls relative /api/v1/... and Vite proxies it to the backend.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
});
