import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: "*.ch.spec.ts",
  timeout: 60_000,
  retries: 1,
  reporter: [["html", { outputFolder: "e2e-ch-report", open: "never" }]],
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
  // Expects the Next.js dev server and full Pulse stack to already be running.
  // Run: cd deploy && ./scripts/start.sh -d
  //      cd pulse-web-otel/examples/nextjs-demo && yarn dev
});
