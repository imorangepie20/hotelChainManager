import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e", testMatch: "customer-saved-draft-preview.spec.ts", reporter: "list",
  use: { baseURL: "http://127.0.0.1:4000", trace: "off" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: { command: "pnpm --dir ../apps/web dev", url: "http://127.0.0.1:4000", reuseExistingServer: !process.env.CI, timeout: 60_000 },
});
