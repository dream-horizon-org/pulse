import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: "*.spec.ts",
  testIgnore: "*.ch.spec.ts",
  timeout: 30_000,
  retries: 1,
  reporter: [["html", { outputFolder: "e2e-report", open: "never" }]],
  use: {
    // Dedicated port so E2E always spawns this demo (avoids clashing with :3003 dev servers).
    baseURL: "http://localhost:3013",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "yarn dev:e2e",
    url: "http://localhost:3013",
    reuseExistingServer: !process.env["CI"],
    timeout: 60_000,
  },
});
