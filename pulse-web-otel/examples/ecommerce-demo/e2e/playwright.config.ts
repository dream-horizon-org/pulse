import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E config for the ecommerce-demo SDK harness.
 *
 * Located inside ecommerce-demo/ because the tests exercise the demo app's
 * behaviour, and @playwright/test is a demo dev-dependency (not part of the SDK).
 *
 * The webServer starts Vite in --mode test which loads .env.test:
 *   - OTLP calls go to http://otel-mock.test (intercepted via page.route — no real ingest)
 *   - Batch flush delay = 200ms (fast assertions instead of waiting 5s)
 *   - gzip disabled (plain JSON; fixture also handles gzip transparently)
 *
 * Run all:           yarn e2e                       (from ecommerce-demo/)
 * Single milestone:  yarn e2e --grep "@M1"
 * Single browser:    yarn e2e --project=chromium
 * Headed (debug):    yarn e2e --headed
 *
 * From SDK root:     yarn workspace ecommerce-demo e2e
 */
export default defineConfig({
  testDir: '.',           // specs live alongside this config file in e2e/
  testMatch: '*.spec.ts',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,   // OTLP signal order is time-sensitive; run serially
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: '../e2e-report' }]],

  use: {
    baseURL: 'http://localhost:3002',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox',  use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit',   use: { ...devices['Desktop Safari'] } },
  ],

  webServer: {
    command: 'yarn dev --mode test',
    cwd: '..',              // ecommerce-demo root (one level up from e2e/)
    port: 3002,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
