import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

// Test passwords come from the git-ignored .env in the repository root (or real environment variables).
const rootDir = fileURLToPath(new URL('..', import.meta.url));

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    css: false,
    include: ['src/**/*.test.{js,jsx}'],
    env: loadEnv(mode, rootDir, 'TEST_USER_'),
  },
}));
