import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: "*.spec.ts",
  testIgnore: "*.ch.spec.ts",
  timeout: 30_000,
  retries: 1,
  reporter: [["html", { outputFolder: "e2e-report", open: "never" }]],
  use: {
    baseURL: "http://localhost:3003",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "yarn dev",
    url: "http://localhost:3003",
    reuseExistingServer: !process.env["CI"],
    timeout: 60_000,
  },
});
