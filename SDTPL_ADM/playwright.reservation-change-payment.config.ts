import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'customer-reservation-change-payment.spec.ts',
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4000',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'pnpm --dir ../apps/web run build && pnpm --dir ../apps/web exec vite preview --host 127.0.0.1 --port 4000',
    url: 'http://localhost:4000/reservation-change-payment',
    reuseExistingServer: false,
    timeout: 120_000,
  },
})
