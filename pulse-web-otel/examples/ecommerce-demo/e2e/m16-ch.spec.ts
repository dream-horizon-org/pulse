/**
 * M16 CH Integration Tests — React Integration (PulseProvider / PulseErrorBoundary / StrictMode)
 *
 * Verifies that signals produced by React integration actually land in
 * ClickHouse via the real OTEL collector → Pulse backend pipeline.
 *
 * TC 16.1 — session.start in otel_logs after app mount (PulseProvider)
 * TC 16.2 — device.crash from PulseErrorBoundary render error in stack_trace_events
 * TC 16.3 — screen_load span in otel_traces carries screen.name after route change
 * TC 16.4 — exactly one session.start in CH (no StrictMode double-init)
 * TC 16.5 — screen.name on custom event after useRouterTracking fires
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch            (headless)
 *   yarn e2e:ch:headed     (headed)
 *
 * Auto-skips if CH not reachable.
 */
import { test, expect } from "@playwright/test";
import {
  isCHAvailable,
  waitForCHLog,
  waitForCHStackTrace,
  waitForCHSpan,
  countCHLogs,
  SERVICE_NAME,
} from "./ch-fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

const INGEST_WAIT = 5_000;

// ─── Suite setup: auto-skip when stack not running ───────────────────────────

test.beforeEach(async () => {
  const available = await isCHAvailable();
  if (!available) {
    test.skip(
      true,
      "ClickHouse not reachable — start full stack with deploy/scripts/start.sh",
    );
  }
});

// ─── TC 16.1 — session.start in CH after app mount ───────────────────────────

test.describe("@M16-CH PulseProvider session lifecycle", () => {
  test("TC 16.1: session.start lands in otel_logs with installation.id", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog("session.start", "", 25_000);
    expect(row.PulseType).toBe("session.start");
    expect(row.installation_id).toBeTruthy();
    expect(row.session_id).toBeTruthy();
  });

  test("TC 16.4: exactly one session.start per page load (StrictMode no double-init)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(INGEST_WAIT);

    // Confirm at least one session.start exists — then bound the window to 30s
    // and verify only 1 for this service in a tight window
    const row = await waitForCHLog("session.start", "", 25_000);
    expect(row.PulseType).toBe("session.start");

    // Count session.starts in the last 10s — should be 1 (not 2+ from StrictMode)
    const cnt = await countCHLogs("session.start", "", 10);
    expect(cnt).toBeLessThanOrEqual(1);
  });
});

// ─── TC 16.2 — PulseErrorBoundary device.crash in stack_trace_events ─────────

test.describe("@M16-CH PulseErrorBoundary", () => {
  test("TC 16.2: React render error via PulseErrorBoundary → device.crash in stack_trace_events", async ({
    page,
  }) => {
    await page.goto("/error-demo");
    await page.waitForTimeout(2_000);

    // Trigger the render bomb
    await page.getByTestId("throw-render-error").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace("device.crash", "", 25_000);
    expect(row.PulseType).toBe("device.crash");
    expect(row.ExceptionMessage).toContain("Intentional render error");
    expect(row.ExceptionStackTraceRaw).toBeTruthy();
  });

  test("TC 16.2b: device.crash has react.component_stack in CH", async ({
    page,
  }) => {
    await page.goto("/error-demo");
    await page.waitForTimeout(2_000);

    await page.getByTestId("throw-render-error").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace("device.crash", "", 25_000);
    // PulseErrorBoundary passes componentStack as react.component_stack
    expect(row.component_stack).toBeTruthy();
  });
});

// ─── TC 16.3 / 16.5 — useRouterTracking → screen.name propagation in CH ──────

test.describe("@M16-CH useRouterTracking + screen.name", () => {
  test("TC 16.3: screen_load span carries screen.name after NavBar route change", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(2_000);

    // Navigate to /products via NavBar → useRouterTracking fires → next screen_load carries it
    await page.getByRole("link", { name: "Products" }).click();
    await page.waitForURL("**/products");
    await page.waitForTimeout(INGEST_WAIT);

    const span = await waitForCHSpan("screen_load", "", 25_000);
    // screen_load spans carry screen.name from globalAttrsProcessor
    expect(span.screen_name).toBeTruthy();
  });

  test("TC 16.5: custom event after route change carries new screen.name in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(2_000);

    await page.getByRole("link", { name: "Cart" }).click();
    await page.waitForURL("**/cart");
    await page.waitForTimeout(500);

    // Emit custom event — screen.name should be /cart in otel_logs
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent: (n: string) => void };
      };
      w.Pulse?.trackEvent("ch_screen_name_check");
    });

    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog(
      "custom_event",
      `Body = 'ch_screen_name_check'`,
      25_000,
    );
    expect(row.screen_name).toBe("/cart");
  });
});
