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
