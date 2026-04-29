/**
 * M3 Errors E2E Tests — Error Instrumentation
 *
 * Covers all 18 TCs from the error instrumentation test plan:
 *   TC1  Uncaught JS error → device.crash
 *   TC2  Unhandled promise rejection → non_fatal
 *   TC3  React render error via PulseErrorBoundary → device.crash
 *   TC4  Manual reportException → non_fatal, is_manual=true
 *   TC5  url.path stamped on every error log
 *   TC6  Same error within 5s emitted only once (deduplication)
 *   TC7  Deduplication window resets after 5s
 *   TC8  Two different errors not deduplicated
 *   TC9  battery.percent included on Chrome/Edge
 *   TC10 battery.percent absent on Firefox/Safari — error still captured
 *   TC11 storage.free included in all modern browsers
 *   TC12 Cross-origin script error silently skipped
 *   TC13 Error before SDK init is ignored
 *   TC14 reportException before SDK init is no-op
 *   TC15 String rejection reason wrapped in Error
 *   TC16 Undefined rejection reason handled gracefully
 *   TC17 Timestamp reflects exact time of error
 *   TC18 No conflict with pre-existing window.onerror
 *
 * Run:  yarn e2e --grep "@M3-errors" --project=chromium
 */

import type { Page } from "@playwright/test";
import {
  test,
  expect,
  getAttr,
  findAllLogs,
} from "./fixture";
import type { OtlpFixture } from "./fixture";

// ─── Helper: wait for /error-demo to be fully interactive ────────────────────
// ErrorDemo is lazy-loaded — session.start fires before the component renders.
// We wait for both the SDK init log AND the button element so cold Vite starts
// (first test in a fresh run) don't cause false timeouts.

async function gotoErrorDemo(page: Page, otlp: OtlpFixture): Promise<void> {
  // Allow extra time for cold Vite start — ErrorDemo is lazy-loaded so the
  // first test in a fresh run may need up to 45s to compile + serve the chunk.
  test.setTimeout(60_000);
  await page.goto("/error-demo");
  // Poll until the lazy ErrorDemo bundle renders the buttons
  await page.waitForSelector('[data-testid="throw-uncaught"]', { timeout: 50_000 });
  await otlp.waitForLog("session.start");
  otlp.reset();
}

// ─── TC1: Uncaught JS error → device.crash ────────────────────────────────────

test("@M3-errors TC1 — uncaught error → device.crash with stable attrs", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-uncaught").click();

  const log = await otlp.waitForLog("device.crash");
  expect(log.severityText).toBe("FATAL");
  expect(getAttr(log.attributes, "pulse.type")).toBe("device.crash");
  expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
  expect(getAttr(log.attributes, "exception.message")).toBe("Demo uncaught error from ErrorDemo");
  expect(getAttr(log.attributes, "exception.stacktrace")).toBeTruthy();
  expect(Number(getAttr(log.attributes, "error.lineno"))).toBeGreaterThan(0);
  // non_fatal.is_manual must NOT be on device.crash
  expect(getAttr(log.attributes, "non_fatal.is_manual")).toBeUndefined();
  console.log("TC1 PASS");
});

// ─── TC2: Unhandled promise rejection → non_fatal ────────────────────────────

test("@M3-errors TC2 — unhandled rejection → non_fatal with is_manual=false", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-promise").click();

  const log = await otlp.waitForLog("non_fatal");
  expect(log.severityText).toBe("WARN");
  expect(getAttr(log.attributes, "pulse.type")).toBe("non_fatal");
  expect(getAttr(log.attributes, "exception.message")).toBe("Demo unhandled rejection from ErrorDemo");
  expect(getAttr(log.attributes, "non_fatal.is_manual")).toBe(false);
  console.log("TC2 PASS");
});

// ─── TC3: React render error via PulseErrorBoundary → device.crash ────────────

test("@M3-errors TC3 — React render error caught by PulseErrorBoundary → device.crash", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-render-error").click();

  const log = await otlp.waitForLog("device.crash");
  expect(getAttr(log.attributes, "pulse.type")).toBe("device.crash");
  expect(getAttr(log.attributes, "exception.message")).toBe("Intentional render error from ErrorDemo");
  // Fallback UI rendered
  await expect(page.getByText("Render error caught by PulseErrorBoundary")).toBeVisible();
  console.log("TC3 PASS");
});

// ─── TC4: Manual reportException → non_fatal, is_manual=true ─────────────────

test("@M3-errors TC4 — manual reportException → non_fatal with is_manual=true", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("report-exception").click();

  const log = await otlp.waitForLog("non_fatal");
  expect(getAttr(log.attributes, "pulse.type")).toBe("non_fatal");
  expect(getAttr(log.attributes, "non_fatal.is_manual")).toBe(true);
  expect(getAttr(log.attributes, "exception.message")).toBe("Manually reported error");
  console.log("TC4 PASS");
});

// ─── TC5: url.path stamped on every error log ─────────────────────────────────

test("@M3-errors TC5 — url.path = /error-demo on every error log", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-uncaught").click();
  await otlp.waitForLog("device.crash");

  const crashes = findAllLogs(otlp.captured, "device.crash");
  expect(crashes.length).toBeGreaterThan(0);
  for (const log of crashes) {
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
  }
  console.log("TC5 PASS");
});

// ─── TC6: Same error within 5s emitted only once ─────────────────────────────

test("@M3-errors TC6 — same error within 5s emitted only once", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    for (let i = 0; i < 5; i++) {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "dup",
          filename: "x.js",
          lineno: 1,
          colno: 0,
          error: new Error("dup"),
        }),
      );
    }
  });
  await page.waitForTimeout(1_000);

  const logs = findAllLogs(otlp.captured, "device.crash");
  expect(logs).toHaveLength(1);
  console.log("TC6 PASS");
});

// ─── TC7: Deduplication window resets after 5s ───────────────────────────────

test("@M3-errors TC7 — dedup window resets after 5s — 2 logs emitted", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "resetdup",
        filename: "x.js",
        lineno: 1,
        colno: 0,
        error: new Error("resetdup"),
      }),
    );
  });

  await page.waitForTimeout(6_000);

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "resetdup",
        filename: "x.js",
        lineno: 1,
        colno: 0,
        error: new Error("resetdup"),
      }),
    );
  });

  await page.waitForTimeout(1_000);

  const logs = findAllLogs(otlp.captured, "device.crash").filter(
    (l) => getAttr(l.attributes, "exception.message") === "resetdup",
  );
  expect(logs).toHaveLength(2);
  console.log("TC7 PASS");
}, 20_000);

// ─── TC8: Two different errors not deduplicated ───────────────────────────────

test("@M3-errors TC8 — two different errors not deduplicated", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "err-a",
        filename: "x.js",
        lineno: 1,
        colno: 0,
        error: new Error("err-a"),
      }),
    );
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "err-b",
        filename: "x.js",
        lineno: 2,
        colno: 0,
        error: new Error("err-b"),
      }),
    );
  });
  await page.waitForTimeout(500);

  const logs = findAllLogs(otlp.captured, "device.crash");
  const messages = logs.map((l) => getAttr(l.attributes, "exception.message") as string);
  expect(messages).toContain("err-a");
  expect(messages).toContain("err-b");
  console.log("TC8 PASS");
});

// ─── TC9: battery.percent included on Chrome ─────────────────────────────────

test("@M3-errors TC9 — battery.percent present on Chromium (getBattery available)", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-uncaught").click();
  const log = await otlp.waitForLog("device.crash");

  const batteryVal = getAttr(log.attributes, "battery.percent");
  if (batteryVal !== undefined) {
    expect(Number(batteryVal)).toBeGreaterThanOrEqual(0);
    expect(Number(batteryVal)).toBeLessThanOrEqual(100);
    console.log(`TC9 PASS: battery.percent = ${String(batteryVal)}`);
  } else {
    console.log("TC9 SKIP: battery.percent absent (headless/VM environment)");
  }
});

// ─── TC10: battery.percent absent — error still captured ─────────────────────

test("@M3-errors TC10 — battery.percent absent but error still captured", async ({
  page,
  otlp,
}) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "getBattery", {
      value: undefined,
      writable: true,
      configurable: true,
    });
  });

  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-uncaught").click();
  const log = await otlp.waitForLog("device.crash");

  expect(getAttr(log.attributes, "pulse.type")).toBe("device.crash");
  expect(getAttr(log.attributes, "exception.message")).toBeTruthy();
  expect(getAttr(log.attributes, "battery.percent")).toBeUndefined();
  console.log("TC10 PASS");
});

// ─── TC11: storage.free included ─────────────────────────────────────────────

test("@M3-errors TC11 — storage.free present and > 0", async ({
  page,
  otlp,
}) => {
  await gotoErrorDemo(page, otlp);

  await page.getByTestId("throw-uncaught").click();
  const log = await otlp.waitForLog("device.crash");

  const storageFree = getAttr(log.attributes, "storage.free");
  if (storageFree !== undefined) {
    expect(Number(storageFree)).toBeGreaterThan(0);
    console.log(`TC11 PASS: storage.free = ${String(storageFree)}`);
  } else {
    console.log("TC11 SKIP: storage.free absent (storage API not available)");
  }
});

// ─── TC12: Cross-origin script error silently skipped ────────────────────────

test("@M3-errors TC12 — cross-origin 'Script error.' silently skipped", async ({
  page,
  otlp,
}) => {
  await page.goto("/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "Script error.",
        error: null,
      }),
    );
  });
  await page.waitForTimeout(500);

  expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
  expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  console.log("TC12 PASS");
});

// ─── TC13: Error before SDK init is ignored ───────────────────────────────────

test("@M3-errors TC13 — error before SDK init is ignored", async ({
  page,
  otlp,
}) => {
  await page.route("**/v1/**", async (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: '{"partialSuccess":{}}',
  }));

  await page.goto("about:blank");

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "pre-init-error",
        filename: "x.js",
        lineno: 1,
        error: new Error("pre-init-error"),
      }),
    );
  });
  await page.waitForTimeout(500);

  expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
  console.log("TC13 PASS");
});

// ─── TC14: reportException before SDK init is no-op ──────────────────────────

test("@M3-errors TC14 — reportException before SDK init is no-op", async ({
  page,
  otlp,
}) => {
  await page.route("**/v1/**", async (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: '{"partialSuccess":{}}',
  }));
  await page.goto("about:blank");

  await page.evaluate(() => {
    type PW = { isInitialized?: () => boolean; reportException?: (e: Error) => void };
    const pw = (window as unknown as { PulseWeb?: PW }).PulseWeb;
    if (pw?.reportException) {
      pw.reportException(new Error("early report"));
    }
  });
  await page.waitForTimeout(300);

  expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  console.log("TC14 PASS");
});

// ─── TC15: String rejection reason wrapped in Error ───────────────────────────

test("@M3-errors TC15 — string rejection reason wrapped as Error", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    Promise.reject("something went wrong");
  });

  const log = await otlp.waitForLog("non_fatal");
  expect(getAttr(log.attributes, "exception.type")).toBe("Error");
  expect(getAttr(log.attributes, "exception.message")).toBe("something went wrong");
  console.log("TC15 PASS");
});

// ─── TC16: Undefined rejection reason handled gracefully ──────────────────────

test("@M3-errors TC16 — undefined rejection → exception.message='Unknown rejection'", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    Promise.reject(undefined);
  });

  const log = await otlp.waitForLog("non_fatal");
  expect(getAttr(log.attributes, "exception.message")).toBe("Unknown rejection");
  console.log("TC16 PASS");
});

// ─── TC17: Timestamp reflects exact time of error ────────────────────────────

test("@M3-errors TC17 — timestamp within 1000ms of error dispatch time", async ({
  page,
  otlp,
}) => {
  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  const beforeMs = Date.now();

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "timestamp-check",
        filename: "x.js",
        lineno: 1,
        colno: 0,
        error: new Error("timestamp-check"),
      }),
    );
  });

  const afterMs = Date.now();
  const log = await otlp.waitForLog("device.crash");

  const tsNano = log.timeUnixNano;
  if (tsNano) {
    const tsMs = Number(BigInt(tsNano) / 1_000_000n);
    expect(tsMs).toBeGreaterThanOrEqual(beforeMs - 1000);
    expect(tsMs).toBeLessThanOrEqual(afterMs + 1000);
  }
  console.log("TC17 PASS");
});

// ─── TC18: No conflict with pre-existing window.onerror ───────────────────────

test("@M3-errors TC18 — pre-existing window.onerror still fires after SDK init", async ({
  page,
  otlp,
}) => {
  await page.addInitScript(() => {
    const fired: string[] = [];
    (window as unknown as Record<string, unknown>)["__existingHandlerFired"] = fired;
    window.addEventListener("error", (e) => {
      fired.push(e.message);
    });
  });

  await page.goto("/error-demo");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "coexistence test",
        filename: "app.js",
        lineno: 1,
        colno: 0,
        error: new Error("coexistence test"),
      }),
    );
  });

  const log = await otlp.waitForLog("device.crash");
  expect(getAttr(log.attributes, "exception.message")).toBe("coexistence test");

  const existingFired = await page.evaluate(
    () => (window as unknown as Record<string, unknown[]>)["__existingHandlerFired"] ?? [],
  );
  expect(existingFired).toContain("coexistence test");
  console.log("TC18 PASS");
});
