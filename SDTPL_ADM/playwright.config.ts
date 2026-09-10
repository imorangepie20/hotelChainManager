import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  reporter: "list",
  use: { baseURL: "http://localhost:4001", trace: "on-first-retry" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "node node_modules/next/dist/bin/next build && node node_modules/next/dist/bin/next start --port 4001",
    url: "http://localhost:4001",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
