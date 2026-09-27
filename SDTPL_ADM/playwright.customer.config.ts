import { defineConfig, devices } from "@playwright/test";

const port = process.env.CUSTOMER_PREVIEW_E2E_PORT ?? "4000";
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: "./e2e", testMatch: "customer-saved-draft-preview.spec.ts", reporter: "list",
  use: { baseURL, trace: "off" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: { command: `pnpm --dir ../apps/web dev --port ${port}`, url: baseURL, reuseExistingServer: !process.env.CI, timeout: 60_000 },
});
