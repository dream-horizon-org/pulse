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
    env: {
      // Speed up log batch flushing in E2E — resolveScheduledDelay() in
      // src/constants/exporters.ts reads this via process.env (Next.js bakes
      // NEXT_PUBLIC_* env vars into the client bundle at build time).
      NEXT_PUBLIC_PULSE_BATCH_DELAY_MS: "500",
    },
  },
});
