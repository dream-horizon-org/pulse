/**
 * Web Vitals (Plan B) — OTLP logs with pulse.type=web_vital.
 * In `--mode test`, {@code VITE_PULSE_BATCH_DELAY_MS=200} — wait ~1.5s after click for log export.
 */
import { test, expect, findAllLogs, getAttr } from "./fixture";
import {
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  blockActiveConfigFetch,
} from "./test-sdk-config";

test.describe("@WebVitals", () => {
  test("emits TTFB web_vital log after load and batch window", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const ttfb = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "TTFB",
    );
    expect(ttfb).toBeDefined();
    expect(getAttr(ttfb!.attributes, "pulse.type")).toBe("web_vital");
    const value = getAttr(ttfb!.attributes, "web_vital.value");
    expect(typeof value).toBe("number");
    expect(Number.isFinite(value as number)).toBe(true);
    const rating = getAttr(ttfb!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(rating);
    expect(getAttr(ttfb!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(ttfb!.attributes, "screen.name")).toBeTruthy();
  });

  test("emits FCP web_vital log after load and batch window", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const fcp = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "FCP",
    );
    expect(fcp).toBeDefined();
    expect(getAttr(fcp!.attributes, "pulse.type")).toBe("web_vital");
    const value = getAttr(fcp!.attributes, "web_vital.value");
    expect(typeof value).toBe("number");
    expect(Number.isFinite(value as number)).toBe(true);
    const rating = getAttr(fcp!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(rating);
    expect(getAttr(fcp!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(fcp!.attributes, "screen.name")).toBeTruthy();
  });

  test("emits LCP web_vital log after click and batch window", async ({
    page,
    otlp,
  }) => {
    // Rely on fixture `attachDefaultSdkConfigStub` (404 active-config + OPTIONS CORS).
    // Extra `blockActiveConfigFetch` routes can shadow the stub and break preflight.

    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.waitForTimeout(500);
    await page.click("body");
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const lcp = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "LCP",
    );
    expect(lcp).toBeDefined();
    expect(getAttr(lcp!.attributes, "pulse.type")).toBe("web_vital");
    const value = getAttr(lcp!.attributes, "web_vital.value");
    expect(typeof value).toBe("number");
    expect(Number.isFinite(value as number)).toBe(true);
    const rating = getAttr(lcp!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(rating);
    expect(getAttr(lcp!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(lcp!.attributes, "screen.name")).toBeTruthy();
  });

  test("emits INP web_vital log on tab hide after real interaction", async ({
    page,
    otlp,
    browserName,
  }) => {
    // PerformanceEventTiming entries are only generated for programmatic clicks in Chromium.
    test.skip(
      browserName !== "chromium",
      "PerformanceEventTiming is Chromium-only in Playwright",
    );

    await page.goto("/");
    await otlp.waitForLog("session.start");

    // Inflate interaction processing time above PerformanceEventTiming's 40ms minimum.
    // Headless Playwright clicks settle in < 5ms otherwise — too fast to be an INP candidate.
    await page.waitForSelector('a[href="/products"]');
    await page.evaluate(() => {
      document.querySelector('a[href="/products"]')?.addEventListener(
        "click",
        () => {
          const end = Date.now() + 70;
          while (Date.now() < end) {}
        },
        { once: true },
      );
    });
    await page.click('a[href="/products"]');

    // Let the PerformanceObserver callback process the event entry before simulating hide.
    await page.waitForTimeout(300);

    // Simulate tab hide — triggers web-vitals INP callback and SDK loggerProvider.forceFlush().
    await page.evaluate(() => {
      Object.defineProperty(document, "visibilityState", {
        get: () => "hidden",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));
    });

    await page.waitForTimeout(1000);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const inp = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "INP",
    );
    expect(inp).toBeDefined();
    expect(getAttr(inp!.attributes, "pulse.type")).toBe("web_vital");
    const inpValue = getAttr(inp!.attributes, "web_vital.value");
    expect(typeof inpValue).toBe("number");
    expect(Number.isFinite(inpValue as number)).toBe(true);
    const inpRating = getAttr(inp!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(inpRating);
    expect(getAttr(inp!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(inp!.attributes, "screen.name")).toBeTruthy();
  });

  test("emits FID web_vital log on first interaction (Chromium)", async ({
    page,
    otlp,
    browserName,
  }) => {
    test.skip(
      browserName !== "chromium",
      "First-input / PerformanceEventTiming is Chromium-reliable in Playwright",
    );

    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.waitForSelector('a[href="/products"]');
    await page.evaluate(() => {
      document.querySelector('a[href="/products"]')?.addEventListener(
        "click",
        () => {
          const end = Date.now() + 70;
          while (Date.now() < end) {}
        },
        { once: true },
      );
    });
    await page.click('a[href="/products"]');
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const fid = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "FID",
    );
    expect(fid).toBeDefined();
    expect(getAttr(fid!.attributes, "pulse.type")).toBe("web_vital");
    const fidValue = getAttr(fid!.attributes, "web_vital.value");
    expect(typeof fidValue).toBe("number");
    expect(Number.isFinite(fidValue as number)).toBe(true);
    const fidRating = getAttr(fid!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(fidRating);
    expect(getAttr(fid!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(fid!.attributes, "screen.name")).toBeTruthy();
  });

  test("emits CLS web_vital after layout shift and tab hide", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.evaluate(async () => {
      const box = document.createElement("div");
      box.setAttribute("data-e2e-cls-shift", "1");
      box.style.cssText =
        "width:80px;height:80px;background:#ef4444;position:fixed;top:12px;left:12px;z-index:99999;";
      document.body.appendChild(box);
      await new Promise<void>((resolve) => {
        requestAnimationFrame(() => {
          box.style.height = "200px";
          resolve();
        });
      });
    });

    await page.waitForTimeout(300);

    await page.evaluate(() => {
      Object.defineProperty(document, "visibilityState", {
        get: () => "hidden",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));
    });

    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const cls = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "CLS",
    );
    expect(cls).toBeDefined();
    expect(getAttr(cls!.attributes, "pulse.type")).toBe("web_vital");
    const clsValue = getAttr(cls!.attributes, "web_vital.value");
    expect(typeof clsValue).toBe("number");
    expect(Number.isFinite(clsValue as number)).toBe(true);
    const clsRating = getAttr(cls!.attributes, "web_vital.rating");
    expect(["good", "needs-improvement", "poor"]).toContain(clsRating);
    expect(getAttr(cls!.attributes, "session.id")).toBeTruthy();
    expect(getAttr(cls!.attributes, "screen.name")).toBeTruthy();
  });

  test("SPA navigation flushes TTFB vital with correct screen.name from initial route", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    // Let TTFB/FCP fire and buffer; do not trigger tab hide.
    await page.waitForTimeout(400);

    // SPA navigation — triggers notifySoftNavigation flush in the hook.
    await page.click('a[href="/products"]');
    await page.waitForTimeout(600); // batch window 200ms + buffer

    const vitals = findAllLogs(otlp.captured, "web_vital");
    const ttfb = vitals.find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "TTFB",
    );
    expect(ttfb).toBeDefined();
    expect(getAttr(ttfb!.attributes, "pulse.type")).toBe("web_vital");
    // screen.name must be "/" — TTFB was measured on the home route.
    expect(getAttr(ttfb!.attributes, "screen.name")).toBe("/");
    expect(getAttr(ttfb!.attributes, "session.id")).toBeTruthy();
  });

  test("does not emit web_vital logs when web_vitals feature gate is disabled", async ({
    page,
    otlp,
  }) => {
    await seedPulseSdkConfig(
      page,
      minimalPulseSdkConfig({
        features: [
          {
            featureName: "web_vitals",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
          },
        ],
      }),
    );
    await blockActiveConfigFetch(page);

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.waitForTimeout(500);
    await page.click("body");
    await page.waitForTimeout(1500);

    expect(findAllLogs(otlp.captured, "web_vital")).toHaveLength(0);
  });
});
