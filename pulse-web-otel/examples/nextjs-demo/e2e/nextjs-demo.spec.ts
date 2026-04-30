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
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Boundary crash from error-demo",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
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
  });

  test("reportDeviceCrash emits device.crash with exception.type", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "exception.message")).toContain(
      "Manual device.crash",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
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
