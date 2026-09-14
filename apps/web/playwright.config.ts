import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './test', testMatch: 'toss-test-payment.spec.ts', reporter: 'list',
  use: { baseURL: 'http://localhost:4000', ...devices['Desktop Chrome'], trace: 'off' },
  webServer: { command: 'pnpm run dev', url: 'http://localhost:4000', reuseExistingServer: false, timeout: 60000 },
})
