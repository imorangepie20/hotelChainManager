import { defineConfig, devices } from '@playwright/test'

const port = process.env.CUSTOMER_WEB_E2E_PORT ?? '4173'
const baseURL = process.env.CUSTOMER_WEB_BASE_URL ?? `http://127.0.0.1:${port}`
const serverLog = process.env.CUSTOMER_WEB_E2E_SERVER_LOG ?? 'playwright-server.log'
process.env.CUSTOMER_WEB_BASE_URL = baseURL

export default defineConfig({
  testDir: './test',
  testMatch: '**/*.spec.ts',
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  workers: process.env.CI ? 2 : undefined,
  outputDir: 'test-results/artifacts',
  reporter: process.env.CI
    ? [
        ['line'],
        ['html', { outputFolder: 'playwright-report', open: 'never' }],
        ['junit', { outputFile: 'test-results/customer-web-playwright.xml' }],
      ]
    : [
        ['list'],
        ['html', { outputFolder: 'playwright-report', open: 'never' }],
      ],
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `pnpm exec vite preview --host 127.0.0.1 --port ${port} --strictPort > ${serverLog} 2>&1`,
    url: baseURL,
    env: {
      ...process.env,
      API_PROXY_TARGET: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:65535',
      CONCIERGE_PROXY_TARGET: process.env.CONCIERGE_PROXY_TARGET ?? 'http://127.0.0.1:65535',
    },
    reuseExistingServer: false,
    timeout: 180_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
})
