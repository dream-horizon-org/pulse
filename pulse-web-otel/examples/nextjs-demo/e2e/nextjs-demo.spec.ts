/**
 * Next.js Demo — E2E Tests (mock OTLP, no ClickHouse required)
 *
 * Verifies:
 *   - session.start emitted on first page load
 *   - screen.name updates on App Router navigation
 *   - Session ID persists across navigations (same session)
 *   - device.crash emitted on PulseErrorBoundary catch
 *   - device.crash emitted on manual reportDeviceCrash
 *   - non_fatal emitted on manual reportException
 *   - platform resource attribute = "web"
 *
 * Run: yarn workspace nextjs-demo e2e
 */
import {
  test,
  expect,
  getAttr,
  findAllLogs,
  getResourceAttr,
} from "./fixture";

// ─── Session lifecycle ────────────────────────────────────────────────────────

test.describe("session lifecycle", () => {
  test("session.start emitted on first page load", async ({ page, otlp }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  });

  test("platform resource attribute is 'web'", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
  });

  test("session ID is consistent across navigations", async ({ page, otlp }) => {
    await page.goto("/");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;
    expect(sessionId).toBeTruthy();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");

    const allLogs = findAllLogs(otlp.captured, "session.start");
    expect(allLogs.length).toBe(1);
    expect(getAttr(allLogs[0].attributes, "session.id")).toBe(sessionId);
  });
});

// ─── Screen tracking ──────────────────────────────────────────────────────────

test.describe("screen tracking — App Router navigation", () => {
  test("screen.name updates when navigating from Home to Products", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    const deadline = Date.now() + 8_000;
    let found = false;
    while (Date.now() < deadline) {
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      if (logs.some((lr) => getAttr(lr.attributes, "screen.name") === "/products")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected a log with screen.name = /products").toBe(true);
  });

  test("screen.name reflects /cart after navigating to cart", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");

    const deadline = Date.now() + 8_000;
    let found = false;
    while (Date.now() < deadline) {
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      if (logs.some((lr) => getAttr(lr.attributes, "screen.name") === "/cart")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected a log with screen.name = /cart").toBe(true);
  });

  test("screen.name updates on multi-hop navigation: / → /products → /cart", async ({
    page,
    otlp,
  }) => {
    const allScreenNames = (): string[] => {
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      return logs
        .map((lr) => getAttr(lr.attributes, "screen.name") as string)
        .filter(Boolean);
    };

    const waitForScreenName = async (name: string): Promise<void> => {
      const deadline = Date.now() + 8_000;
      while (Date.now() < deadline) {
        if (allScreenNames().includes(name)) return;
        await new Promise((r) => setTimeout(r, 100));
      }
      throw new Error(`Timeout waiting for screen.name = ${name}`);
    };

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");
    await waitForScreenName("/products");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");
    await waitForScreenName("/cart");

    const names = allScreenNames();
    expect(names).toContain("/products");
    expect(names).toContain("/cart");
  });
});

// ─── Error tracking ───────────────────────────────────────────────────────────

test.describe("error tracking", () => {
  test("PulseErrorBoundary emits device.crash when component throws", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='throw-btn']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-15: exception.message exact value
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Boundary crash from error-demo",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-12: SeverityText must be "FATAL" not "ERROR"
    expect(log.severityText).toBe("FATAL");
    // ERR-13: SeverityNumber must be 21 (OTel FATAL2)
    expect(log.severityNumber).toBe(21);
  });

  test("reportException emits non_fatal with exception.type", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='manual-exception-btn']");

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "exception.message")).toContain(
      "Manual non_fatal",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-25: SeverityText must be "WARN" not "ERROR" or "FATAL"
    expect(log.severityText).toBe("WARN");
    // ERR-26: SeverityNumber must be 13 (OTel WARN)
    expect(log.severityNumber).toBe(13);
    // ERR-08 / ERR-20: url.path = pathname only, not full URL
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(String(getAttr(log.attributes, "url.path") ?? "").startsWith("http")).toBe(false);
  });

  test("reportDeviceCrash emits device.crash with exception.type", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-15: exception.message exact value
    expect(getAttr(log.attributes, "exception.message")).toContain(
      "Manual device.crash",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-08 / ERR-20: url.path = pathname only, not full URL
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(String(getAttr(log.attributes, "url.path") ?? "").startsWith("http")).toBe(false);
  });

  test("device.crash carries session.id (error is associated to session)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='throw-btn']");

    const crash = await otlp.waitForLog("device.crash");
    expect(getAttr(crash.attributes, "session.id")).toBe(sessionId);
  });

  test("non_fatal carries same session.id as session.start", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-exception-btn']");

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });

  test("manual device.crash carries same session.id as session.start", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });
});

test.describe("error signal contract", () => {
  test("ERR-02 / ERR-31 — unhandled rejection emits non_fatal WARN, is_manual=false (boolean)", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = Promise.reject(new Error("test rejection"));
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: new Error("test rejection"),
        }),
      );
    });

    const log = await otlp.waitForLog("non_fatal");
    // ERR-02
    expect(log.severityText).toBe("WARN");
    expect(log.severityNumber).toBe(13);
    // ERR-31: boolean false, not string "false"
    const isManual = getAttr(log.attributes, "non_fatal.is_manual");
    expect(isManual).toBe(false);
    expect(typeof isManual).toBe("boolean");
  });

  test("ERR-05 — handled try/catch does not emit device.crash", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      try {
        throw new Error("caught and swallowed");
      } catch {
        // intentionally swallowed — must NOT reach window.onerror
      }
    });

    await page.waitForTimeout(700);
    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
  });

  test("ERR-17 — error.filename is defined (bundle URL or unknown, never absent)", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "filename test",
          error: new Error("filename test"),
        }),
      );
    });

    const log = await otlp.waitForLog("device.crash");
    const filename = getAttr(log.attributes, "error.filename");
    // Must be present — empty string only allowed for cross-origin stubs
    expect(filename).toBeDefined();
    // If non-empty and not "unknown", must be a bundle URL
    if (filename && filename !== "" && filename !== "unknown") {
      expect(String(filename).startsWith("http")).toBe(true);
    }
  });

  test("ERR-03 — same error burst within 5s emits only once (dedup)", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("[data-testid='throw-burst']");
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter(
      (log) => getAttr(log.attributes, "exception.message") === "Burst dedup error",
    );
    expect(crashes).toHaveLength(1);
  });

  test("ERR-09 / ERR-14 / ERR-16 — TypeError: class name preserved + stacktrace multi-line", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("[data-testid='throw-type-error']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-14: class name preserved
    expect(getAttr(log.attributes, "exception.type")).toBe("TypeError");
    // ERR-09 / ERR-16: stacktrace must be multi-line
    const stack = String(getAttr(log.attributes, "exception.stacktrace") ?? "");
    expect(stack.includes("\n")).toBe(true);
  });
});
