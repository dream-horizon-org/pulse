/**
 * Playwright config for CH integration tests (m4-ch.spec.ts, m5-ch.spec.ts).
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch                         (from examples/ecommerce-demo/)
 *   yarn e2e:ch:headed                  (headed for debugging)
 *   CH_HOST=http://localhost:8123 yarn e2e:ch
 *
 * Key differences from playwright.config.ts:
 *   - Chromium only (CH verification is browser-independent)
 *   - Longer timeouts (spans flow through collector → CH ~5–8s)
 *   - Vite dev server at port 3098 with 1s batch delay
 *   - NO page.route() OTLP intercept — spans flow to real collector
 */
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: ["m4-ch.spec.ts", "m5-ch.spec.ts"],
  timeout: 60_000,
  expect: { timeout: 25_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "../e2e-report-ch" }],
  ],

  use: {
    baseURL: "http://localhost:3098",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],

  webServer: {
    command: "VITE_PULSE_BATCH_DELAY_MS=1000 VITE_PULSE_COMPRESSION=none yarn dev --port 3098",
    cwd: "..",
    port: 3098,
    reuseExistingServer: true,
    timeout: 60_000,
    env: {
      VITE_PULSE_BATCH_DELAY_MS: "1000",
      VITE_PULSE_COMPRESSION: "none",
      VITE_PULSE_SERVICE_NAME: "ecommerce-demo",
      VITE_PULSE_API_KEY: "default-project_devkey456",
    },
  },
});
