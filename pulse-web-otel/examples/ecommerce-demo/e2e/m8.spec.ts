/**
 * M8 E2E Tests — Pagehide Flush Pipeline
 *
 * Verifies that the SDK's pagehide listener correctly:
 *   - flushes pending OTLP signals when the page unloads (persisted=false)
 *   - skips flush on BFCache restore (persisted=true)
 *   - exports the session.end log on real navigation away
 *   - emits signals that were buffered just before pagehide
 *
 * All OTLP is intercepted via page.route — no real collector needed.
 *
 * Run:  yarn e2e --grep "@M8" --project=chromium
 */
import {
  test,
  expect,
  findAllLogs,
  getAttr,
  findAllLogsByBody,
} from "./fixture";

// ─── TC 8.2 / 8.7 — non-BFCache pagehide triggers flush + session.end ────────

test.describe("@M8 pagehide flush", () => {
  test("TC 8.2/8.7: pagehide (persisted=false) flushes pending signals — session.end arrives", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Dispatch synthetic non-BFCache pagehide
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    const endLog = await otlp.waitForLog("session.end", 8_000);
    expect(getAttr(endLog.attributes, "session.id")).toBeTruthy();
  });

  test("TC 8.3: BFCache pagehide (persisted=true) does NOT emit session.end", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
      );
    });

    await page.waitForTimeout(600);
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);
  });

  test("TC 8.8: signals emitted just before pagehide are flushed", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Emit a custom event and immediately trigger pagehide
    await page.evaluate(() => {
      const w = window as unknown as {
        PulseWeb?: { trackEvent: (n: string) => void };
      };
      w.PulseWeb?.trackEvent("pre_pagehide_event");
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    const eventLog = await otlp.waitForLogByBody("pre_pagehide_event", 8_000);
    expect(eventLog).toBeDefined();
  });

  test("TC 8.8b: real navigation away → session.end reaches OTLP", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Navigate away — fires real pagehide while Playwright page.route still intercepts
    await page.goto("about:blank");

    const endLog = await otlp.waitForLog("session.end", 10_000);
    expect(getAttr(endLog.attributes, "session.end_reason")).toBe("page_unload");
  });

  test("TC 8.2b: only one session.end emitted on pagehide (no duplicates)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    await otlp.waitForLog("session.end", 8_000);
    await page.waitForTimeout(800);

    expect(findAllLogs(otlp.captured, "session.end").length).toBe(1);
  });
});

// ─── TC 8.9 / 8.10 — lifecycle guard: post-shutdown pagehide is silent ────────

test.describe("@M8 pagehide post-shutdown no-op", () => {
  test("TC 8.10: pagehide after shutdown emits no new logs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Shut down the SDK via window.PulseWeb
    await page.evaluate(async () => {
      const w = window as unknown as {
        PulseWeb?: { shutdown?: () => Promise<void> };
      };
      await w.PulseWeb?.shutdown?.();
    });

    await page.waitForTimeout(300);
    otlp.reset();

    // Dispatch pagehide after shutdown — should be silent
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    await page.waitForTimeout(800);
    expect(otlp.captured.length).toBe(0);
  });
});

// ─── TC 8.9 — double pagehide within BFCache cycle ───────────────────────────

test.describe("@M8 BFCache cycle", () => {
  test("TC 8.9b: persisted=true then persisted=false emits exactly one session.end", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Simulate BFCache navigation away (persisted=true — cached, no unload)
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
      );
    });
    await page.waitForTimeout(200);
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);

    // Then real unload (persisted=false)
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    await otlp.waitForLog("session.end", 8_000);
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(1);
  });
});
