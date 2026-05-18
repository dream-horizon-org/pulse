import { defineConfig, devices } from "@playwright/test";

/** Production server: one `next build` up front, then fast steady pages (no dev compile). */
const useProdServer = process.env["PLAYWRIGHT_NEXT_START"] === "1";

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
    command: useProdServer ? "yarn build && yarn start:e2e" : "yarn dev:e2e",
    url: "http://localhost:3013",
    reuseExistingServer: !process.env["CI"],
    timeout: useProdServer ? 180_000 : 60_000,
    env: {
      // Speed up log batch flushing in E2E — resolveScheduledDelay() in
      // src/constants/exporters.ts reads this via process.env (Next.js bakes
      // NEXT_PUBLIC_* env vars into the client bundle at build time).
      NEXT_PUBLIC_PULSE_BATCH_DELAY_MS: "500",
    },
  },
});
