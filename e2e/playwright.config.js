import { defineConfig, devices } from '@playwright/test';

// Test passwords come from the git-ignored .env in the repository root; real environment variables win.
try {
  process.loadEnvFile(new URL('../.env', import.meta.url));
} catch {
  // No .env file: the variables must already be in the environment (checked in credentials.js).
}

// Base URL comes from the environment; the default is the local Compose frontend on loopback.
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
});
