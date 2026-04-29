/**
 * M3 CH Integration Tests — Error Instrumentation
 *
 * Verifies that ErrorInstrumentation log records emitted by the browser
 * actually land in ClickHouse via the real OTEL collector → Pulse backend pipeline.
 *
 * NOTE: device.crash / non_fatal logs are routed by the collector to the
 * Pulse backend (logs/to-backend pipeline), which writes them to the
 * `stack_trace_events` table — NOT `otel_logs`.
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch            (headless)
 *   yarn e2e:ch:headed     (headed)
 *
 * Each test:
 *   1. Drives the browser (real OTLP export — no page.route intercept)
 *   2. Waits INGEST_WAIT ms for batch flush + collector → backend → CH
 *   3. Queries CH and asserts on the row
 *
 * Auto-skips if CH not reachable (stack not running).
 */

import { test, expect } from "@playwright/test";
import {
  isCHAvailable,
  chQuery,
  pollUntilCH,
  SERVICE_NAME,
} from "./ch-fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

const INGEST_WAIT = 5_000;
const CH_DB = process.env["CH_DB"] ?? "otel";

// ─── CH row type for stack_trace_events ──────────────────────────────────────

interface ChStackTraceRow {
  log_ts: string;
  PulseType: string;
  ExceptionMessage: string;
  ExceptionType: string;
  ExceptionStackTraceRaw: string;
  ScreenName: string;
  error_lineno: string;
  non_fatal_is_manual: string;
  battery_percent: string;
  storage_free: string;
  url_path: string;
}

// ─── Query helpers ────────────────────────────────────────────────────────────

function baseWhere(extraSeconds = 120): string {
  return `ResourceAttributes['service.name'] = '${SERVICE_NAME}' AND Timestamp > now() - INTERVAL ${extraSeconds} SECOND`;
}

function waitForCHStackTrace(
  pulseType: "device.crash" | "non_fatal",
  extraWhere = "",
  timeoutMs = 25_000,
): Promise<ChStackTraceRow> {
  const sql = `
    SELECT
      toString(Timestamp)                          AS log_ts,
      PulseType,
      ExceptionMessage,
      ExceptionType,
      ExceptionStackTraceRaw,
      ScreenName,
      LogAttributes['error.lineno']                AS error_lineno,
      LogAttributes['non_fatal.is_manual']         AS non_fatal_is_manual,
      LogAttributes['battery.percent']             AS battery_percent,
      LogAttributes['storage.free']                AS storage_free,
      LogAttributes['url.path']                    AS url_path
    FROM ${CH_DB}.stack_trace_events
    WHERE ${baseWhere()}
      AND PulseType = '${pulseType}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    ORDER BY Timestamp DESC
    LIMIT 1
    FORMAT JSONEachRow
  `;
  return pollUntilCH<ChStackTraceRow>(sql, timeoutMs, `${pulseType} stack trace`);
}

async function countCHStackTraces(
  pulseType: string,
  extraWhere = "",
  windowSeconds = 30,
): Promise<number> {
  const sql = `
    SELECT count() AS cnt
    FROM ${CH_DB}.stack_trace_events
    WHERE ResourceAttributes['service.name'] = '${SERVICE_NAME}'
      AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
      AND PulseType = '${pulseType}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    FORMAT JSONEachRow
  `;
  const rows = await chQuery<{ cnt: string }>(sql);
  return Number(rows[0]?.cnt ?? 0);
}

// ─── Suite setup ──────────────────────────────────────────────────────────────

test.beforeEach(async () => {
  const available = await isCHAvailable();
  if (!available) {
    test.skip(true, "ClickHouse not reachable — start full stack with deploy/scripts/start.sh");
  }
});

// ─── TC1: Uncaught error → device.crash in CH ────────────────────────────────

test.describe("@M3-CH device.crash basic", () => {
  test("TC1: uncaught error → device.crash in CH with stable attrs", async ({ page }) => {
    await page.goto("/error-demo");
    await page.getByTestId("throw-uncaught").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace(
      "device.crash",
      `ExceptionMessage = 'Demo uncaught error from ErrorDemo'`,
    );

    expect(row.PulseType).toBe("device.crash");
    expect(row.ExceptionType).toBeTruthy();
    expect(row.ExceptionMessage).toBe("Demo uncaught error from ErrorDemo");
    expect(row.ExceptionStackTraceRaw).toBeTruthy();
    expect(Number(row.error_lineno)).toBeGreaterThan(0);
    console.log("TC1 PASS");
  });

  test("TC2: unhandled rejection → non_fatal in CH", async ({ page }) => {
    await page.goto("/error-demo");
    await page.getByTestId("throw-promise").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace(
      "non_fatal",
      `ExceptionMessage = 'Demo unhandled rejection from ErrorDemo'`,
    );

    expect(row.PulseType).toBe("non_fatal");
    expect(row.ExceptionMessage).toBe("Demo unhandled rejection from ErrorDemo");
    expect(row.non_fatal_is_manual).toBe("false");
    console.log("TC2 PASS");
  });
});

// ─── TC3: React render error in CH ───────────────────────────────────────────

test.describe("@M3-CH React render error", () => {
  test("TC3: PulseErrorBoundary → device.crash in CH with render error message", async ({ page }) => {
    await page.goto("/error-demo");
    await page.getByTestId("throw-render-error").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace(
      "device.crash",
      `ExceptionMessage = 'Intentional render error from ErrorDemo'`,
    );

    expect(row.PulseType).toBe("device.crash");
    expect(row.ExceptionMessage).toBe("Intentional render error from ErrorDemo");
    console.log("TC3 PASS");
  });
});

// ─── TC4: Manual reportException in CH ───────────────────────────────────────

test.describe("@M3-CH manual reportException", () => {
  test("TC4: reportException → non_fatal in CH with is_manual=true", async ({ page }) => {
    await page.goto("/error-demo");
    await page.getByTestId("report-exception").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace(
      "non_fatal",
      `LogAttributes['non_fatal.is_manual'] = 'true'`,
    );

    expect(row.PulseType).toBe("non_fatal");
    expect(row.non_fatal_is_manual).toBe("true");
    expect(row.ExceptionMessage).toBe("Manually reported error");
    console.log("TC4 PASS");
  });
});

// ─── TC5: url.path in CH ─────────────────────────────────────────────────────

test.describe("@M3-CH url.path", () => {
  test("TC5: url.path = /error-demo on device.crash in CH", async ({ page }) => {
    await page.goto("/error-demo");
    await page.getByTestId("throw-uncaught").click();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHStackTrace(
      "device.crash",
      `LogAttributes['url.path'] = '/error-demo'`,
    );

    expect(row.url_path).toBe("/error-demo");
    console.log("TC5 PASS");
  });
});

// ─── TC6: Deduplication → only 1 entry in CH ─────────────────────────────────

test.describe("@M3-CH deduplication", () => {
  test("TC6: same error 5x within 5s → exactly 1 entry in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/error-demo");

    await page.evaluate(() => {
      for (let i = 0; i < 5; i++) {
        window.dispatchEvent(
          new ErrorEvent("error", {
            message: "ch-dedup-test",
            filename: "x.js",
            lineno: 1,
            colno: 0,
            error: new Error("ch-dedup-test"),
          }),
        );
      }
    });

    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHStackTraces(
      "device.crash",
      `ExceptionMessage = 'ch-dedup-test'`,
      windowSeconds,
    );

    expect(count).toBe(1);
    console.log("TC6 PASS: exactly 1 entry in CH");
  });
});

// ─── TC9: consent=DENIED → no error entries in CH ────────────────────────────

test.describe("@M3-CH consent / lifecycle", () => {
  test("TC9: consent=DENIED → zero error entries in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/?pulse_consent=denied");

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "ch-consent-denied-test",
          filename: "x.js",
          lineno: 1,
          error: new Error("ch-consent-denied-test"),
        }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHStackTraces(
      "device.crash",
      `ExceptionMessage = 'ch-consent-denied-test'`,
      windowSeconds,
    );

    expect(count).toBe(0);
    console.log("TC9 PASS: 0 entries in CH with DENIED consent");
  });

  test("TC10: post-shutdown → no error entries in CH after SDK shutdown", async ({ page }) => {
    type PulseWebWindow = Window & {
      PulseWeb?: { isInitialized: () => boolean; shutdown: () => Promise<void> };
    };

    await page.goto("/error-demo");
    await expect
      .poll(
        () => page.evaluate(() => (window as unknown as PulseWebWindow).PulseWeb?.isInitialized?.() ?? false),
        { timeout: 15_000 },
      )
      .toBe(true);

    await page.evaluate(async () => {
      await (window as unknown as PulseWebWindow).PulseWeb!.shutdown();
    });

    const before = Date.now();

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "ch-post-shutdown-test",
          filename: "x.js",
          lineno: 1,
          error: new Error("ch-post-shutdown-test"),
        }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHStackTraces(
      "device.crash",
      `ExceptionMessage = 'ch-post-shutdown-test'`,
      windowSeconds,
    );

    expect(count).toBe(0);
    console.log("TC10 PASS: 0 entries after shutdown");
  });
});

// ─── TC12: cross-origin excluded ─────────────────────────────────────────────

test.describe("@M3-CH cross-origin excluded", () => {
  test("TC12: cross-origin 'Script error.' → no entry in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/");

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "Script error.",
          error: null,
        }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const sql = `
      SELECT count() AS cnt
      FROM ${CH_DB}.stack_trace_events
      WHERE ResourceAttributes['service.name'] = '${SERVICE_NAME}'
        AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
        AND PulseType = 'device.crash'
        AND ExceptionMessage LIKE '%Script error%'
      FORMAT JSONEachRow
    `;
    const rows = await chQuery<{ cnt: string }>(sql);
    expect(Number(rows[0]?.cnt ?? 0)).toBe(0);
    console.log("TC12 PASS: no cross-origin entry in CH");
  });
});
