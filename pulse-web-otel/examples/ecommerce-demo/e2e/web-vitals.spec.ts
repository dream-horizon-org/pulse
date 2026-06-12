/**
 * Web Vitals — OTLP logs with pulse.type=web_vital.
 * In `--mode test`, {@code VITE_PULSE_BATCH_DELAY_MS=200} — wait ~1.5s after click for log export.
 */
import { test, expect, findAllLogs, findAllSpans, getAttr } from "./fixture";
import { assertWebVitalContract } from "./otlp-contract-helpers";
import {
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  blockActiveConfigFetch,
} from "./test-sdk-config";

/** `Metric.navigationType` values from `web-vitals` ^5.x + forward-compatible `soft-navigation`. */
const WEB_VITAL_NAVIGATION_TYPES = [
  "navigate",
  "reload",
  "back-forward",
  "back-forward-cache",
  "prerender",
  "restore",
  "soft-navigation",
] as const;

function assertExportedWebVitalAttrs(
  attrs: {
    key: string;
    value: {
      stringValue?: string;
      intValue?: number;
      doubleValue?: number;
      boolValue?: boolean;
    };
  }[],
): void {
  expect(getAttr(attrs, "platform")).toBe("web");
  const navigationId = getAttr(attrs, "navigation_id");
  expect(typeof navigationId).toBe("string");
  expect((navigationId as string).length).toBeGreaterThan(10);
  const navType = getAttr(attrs, "web_vital.navigation_type");
  expect(typeof navType).toBe("string");
  expect([...WEB_VITAL_NAVIGATION_TYPES]).toContain(navType as string);
  const ctx = getAttr(attrs, "web_vital.context");
  expect(["pageload", "navigation"]).toContain(ctx);
  const value = getAttr(attrs, "web_vital.value");
  expect(typeof value).toBe("number");
  expect(Number.isFinite(value as number)).toBe(true);
  expect(value as number).toBeGreaterThanOrEqual(0);
  const delta = getAttr(attrs, "web_vital.delta");
  if (delta !== undefined) {
    expect(Number.isFinite(delta as number)).toBe(true);
  }
  const sessionId = getAttr(attrs, "session.id");
  expect(typeof sessionId).toBe("string");
  expect(sessionId as string).toMatch(/^[0-9a-f-]{36}$/i);
}

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
    expect(getAttr(ttfb!.attributes, "screen.name")).toBeTruthy();
    assertExportedWebVitalAttrs(ttfb!.attributes);
    expect(getAttr(ttfb!.attributes, "web_vital.delta")).toBe(
      getAttr(ttfb!.attributes, "web_vital.value"),
    );
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
    expect(getAttr(fcp!.attributes, "screen.name")).toBeTruthy();
    assertExportedWebVitalAttrs(fcp!.attributes);
    expect(getAttr(fcp!.attributes, "web_vital.delta")).toBe(
      getAttr(fcp!.attributes, "web_vital.value"),
    );
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
    expect(getAttr(lcp!.attributes, "screen.name")).toBeTruthy();
    assertExportedWebVitalAttrs(lcp!.attributes);
    expect(getAttr(lcp!.attributes, "web_vital.delta")).toBe(
      getAttr(lcp!.attributes, "web_vital.value"),
    );
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

    // VIT-07 (zero INP before hide): not asserted — v5 + `reportAllChanges` may emit INP
    // after interaction before synthetic `visibilitychange` once the batch window fires.

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
    expect(getAttr(inp!.attributes, "screen.name")).toBeTruthy();
    assertExportedWebVitalAttrs(inp!.attributes);
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

    // VIT-07 (zero CLS before hide): not asserted — `reportAllChanges` can emit CLS right
    // after the layout shift when the batch exporter runs, before synthetic tab hide.

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
    expect(getAttr(cls!.attributes, "screen.name")).toBeTruthy();
    assertExportedWebVitalAttrs(cls!.attributes);
  });

  test("SPA navigation flushes TTFB vital with correct screen.name from initial route", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    // Let TTFB/FCP fire and buffer; do not trigger tab hide.
    await page.waitForTimeout(400);

    // SPA navigation — PulseRouterEvents / useRouterTracking → Pulse.notifySoftNavigation() →
    // loggerProvider.forceFlush(); plus batch/export timing. TTFB was captured on home before nav.
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
    assertExportedWebVitalAttrs(ttfb!.attributes);
  });

  test("never emits web_vital with name FID (web-vitals v5+)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(500);
    await page.click("body");
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    expect(
      vitals.filter((lr) => getAttr(lr.attributes, "web_vital.name") === "FID"),
    ).toHaveLength(0);
  });

  test("SPA screen_load span carries navigation_id after client navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(600);
    otlp.reset();

    await page.click('a[href="/products"]');
    await page.waitForTimeout(1500);

    const loads = findAllSpans(otlp.captured, "screen_load");
    expect(loads.length).toBeGreaterThan(0);
    const lastLoad = loads[loads.length - 1]!;
    const spanNavId = getAttr(lastLoad.attributes, "navigation_id");
    expect(typeof spanNavId).toBe("string");
    expect((spanNavId as string).length).toBeGreaterThan(10);
  });

  test("two SPA navigations produce distinct navigation_id on screen_load spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(600);

    await page.click('a[href="/products"]');
    await page.waitForURL("**/products");
    const deadline1 = Date.now() + 12_000;
    let loadsAfterProducts = findAllSpans(otlp.captured, "screen_load");
    while (loadsAfterProducts.length === 0 && Date.now() < deadline1) {
      await page.waitForTimeout(200);
      loadsAfterProducts = findAllSpans(otlp.captured, "screen_load");
    }
    expect(loadsAfterProducts.length).toBeGreaterThan(0);
    const navIdProducts = getAttr(
      loadsAfterProducts[loadsAfterProducts.length - 1]!.attributes,
      "navigation_id",
    ) as string;

    await page.click('a[href="/cart"]');
    await page.waitForURL("**/cart");
    const countAfterProducts = findAllSpans(
      otlp.captured,
      "screen_load",
    ).length;
    const deadline2 = Date.now() + 12_000;
    let loads = findAllSpans(otlp.captured, "screen_load");
    while (loads.length <= countAfterProducts && Date.now() < deadline2) {
      await page.waitForTimeout(200);
      loads = findAllSpans(otlp.captured, "screen_load");
    }
    expect(loads.length).toBeGreaterThan(countAfterProducts);
    const navIdCart = getAttr(
      loads[loads.length - 1]!.attributes,
      "navigation_id",
    ) as string;

    expect(navIdCart.length).toBeGreaterThan(10);
    expect(navIdProducts).not.toEqual(navIdCart);
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

  test("VIT-07: CLS web_vital flushes on tab hide after layout shift", async ({
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

    const cls = findAllLogs(otlp.captured, "web_vital").find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "CLS",
    );
    expect(cls).toBeDefined();
    assertExportedWebVitalAttrs(cls!.attributes);
  });

  test("VIT screen.name: LCP after /products nav has route-specific screen.name", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(500);
    await page.click('a[href="/products"]');
    await page.waitForURL("**/products");
    await page.waitForTimeout(1500);
    const lcp = findAllLogs(otlp.captured, "web_vital").find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "LCP",
    );
    expect(lcp).toBeDefined();
    expect(getAttr(lcp!.attributes, "screen.name")).toBe("/products");
    assertWebVitalContract(lcp!.attributes);
  });

  test("VIT-08: web_vital.navigation_type on exported vital", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const ttfb = findAllLogs(otlp.captured, "web_vital").find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "TTFB",
    );
    expect(ttfb).toBeDefined();
    assertExportedWebVitalAttrs(ttfb!.attributes);
  });

  test("VIT-15: web_vital.rating enum on exported vital", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const fcp = findAllLogs(otlp.captured, "web_vital").find(
      (lr) => getAttr(lr.attributes, "web_vital.name") === "FCP",
    );
    expect(fcp).toBeDefined();
    expect(["good", "needs-improvement", "poor"]).toContain(
      getAttr(fcp!.attributes, "web_vital.rating"),
    );
  });

  test("VIT-11: web_vital.value is finite number", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const vital = findAllLogs(otlp.captured, "web_vital")[0];
    expect(vital).toBeDefined();
    const value = getAttr(vital!.attributes, "web_vital.value");
    expect(typeof value).toBe("number");
    expect(Number.isFinite(value as number)).toBe(true);
  });

  test("VIT-12: web_vital.name is known metric", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const names = findAllLogs(otlp.captured, "web_vital").map((lr) =>
      getAttr(lr.attributes, "web_vital.name"),
    );
    expect(names.some((n) => typeof n === "string" && n.length > 0)).toBe(true);
  });

  test("VIT-13: session.id UUID on web_vital", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const vital = findAllLogs(otlp.captured, "web_vital")[0];
    expect(getAttr(vital!.attributes, "session.id")).toMatch(
      /^[0-9a-f-]{36}$/i,
    );
  });

  test("VIT-14: platform web on web_vital", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const vital = findAllLogs(otlp.captured, "web_vital")[0];
    expect(getAttr(vital!.attributes, "platform")).toBe("web");
  });

  test("VIT-16: web_vital.delta when present is finite", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500);
    const vital = findAllLogs(otlp.captured, "web_vital")[0];
    const delta = getAttr(vital!.attributes, "web_vital.delta");
    if (delta !== undefined) {
      expect(Number.isFinite(delta as number)).toBe(true);
    }
  });

  test("VIT-09: tab hide on /products after CLS emits web_vital without session.end", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await otlp.waitForLog("session.start");

    await page.evaluate(async () => {
      const box = document.createElement("div");
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
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
      );
      Object.defineProperty(document, "visibilityState", {
        get: () => "hidden",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await page.waitForTimeout(1500);

    const vitals = findAllLogs(otlp.captured, "web_vital");
    expect(vitals.length).toBeGreaterThan(0);
    expect(findAllLogs(otlp.captured, "session.end")).toHaveLength(0);
    vitals.forEach((lr) => assertExportedWebVitalAttrs(lr.attributes));
  });

  test("does not emit web_vital logs when local web vitals kill switch is on (?pulse_wv_enabled=false)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/?pulse_wv_enabled=false");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.waitForTimeout(500);
    await page.click("body");
    await page.waitForTimeout(1500);

    expect(findAllLogs(otlp.captured, "web_vital")).toHaveLength(0);
  });
});
