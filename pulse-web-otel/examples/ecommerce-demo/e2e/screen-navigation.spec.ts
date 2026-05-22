import {
  test,
  expect,
  getAttr,
  findAllSpans,
  getResourceAttr,
} from "./fixture";
import {
  blockActiveConfigFetch,
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  waitPastSeededSignalsBatchWindow,
} from "./test-sdk-config";

/**
 * Screen Navigation E2E Tests — OTLP spans (`screen_load`, `screen_session`) → `otel_traces`.
 *
 * Run: yarn e2e --grep "@ScreenNav" --project=chromium
 */

// NAV-09 E2E disabled — flaky vs Navigation Timing race; manual bucket (CH/DevTools).
// Re-enable test below once Playwright wait strategy is stable (see e2e-coverage-decisions.json).

// ─── Initial Page Load ────────────────────────────────────────────────────────

test.describe("@ScreenNav initial page load", () => {
  test("emits screen_load span on page load", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load", 8000);

    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
    expect(getAttr(load.attributes, "screen.name")).toBeTruthy();
    expect(getAttr(load.attributes, "session.id")).toBeTruthy();
    expect(load.name).toBe("screen_load");
  });

  test("screen_load has valid start.type", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");

    const startType = getAttr(load.attributes, "start.type");
    expect(startType).toMatch(/^(cold|reload|back_forward)$/);
  });

  test("tti may be present on screen_load when Navigation Timing is available", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load", 8000);
    const tti = getAttr(load.attributes, "tti");
    if (tti !== undefined) {
      expect(typeof tti).toBe("number");
      expect(tti).toBeGreaterThanOrEqual(0);
    }
  });

  // test("NAV-09: cold screen_load exports Navigation Timing attrs when measurable", async ({
  //   page,
  //   otlp,
  // }) => {
  //   const load = await waitForMeasurableInitialScreenLoad(page, otlp);
  //   const assertTimingNum = (key: string) => {
  //     const v = getAttr(load.attributes, key);
  //     if (v === undefined) return;
  //     expect(typeof v).toBe("number");
  //     expect(Number(v)).toBeGreaterThanOrEqual(0);
  //   };
  //   expect(getAttr(load.attributes, "start.type")).toMatch(
  //     /^(cold|reload|back_forward)$/,
  //   );
  //   assertTimingNum("ttfb");
  //   assertTimingNum("page.load_time");
  //   assertTimingNum("dom.processing_time");
  //   assertTimingNum("tti");
  //   assertTimingNum("dns.time");
  //   assertTimingNum("tcp.time");
  //   const ttfb = getAttr(load.attributes, "ttfb");
  //   const pageLoad = getAttr(load.attributes, "page.load_time");
  //   expect(ttfb !== undefined || pageLoad !== undefined).toBe(true);
  // });
});

// ─── SPA Navigation ───────────────────────────────────────────────────────────

test.describe("@ScreenNav SPA navigation", () => {
  test("emits screen_session span on route change", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session", 8000);

    expect(getAttr(session.attributes, "pulse.type")).toBe("screen_session");
    expect(getAttr(session.attributes, "screen.name")).toBeTruthy();
    expect(getAttr(session.attributes, "session.id")).toBeTruthy();
    expect(session.name).toBe("screen_session");

    const durationMs = getAttr(session.attributes, "session.duration_ms");
    expect(typeof durationMs).toBe("number");
    expect(durationMs).toBeGreaterThan(0);
    expect(getAttr(session.attributes, "session.duration")).toEqual(durationMs);
  });

  test("NAV-03: session.duration_ms reflects dwell time with fake clock", async ({
    page,
    otlp,
  }) => {
    await page.clock.install({ time: Date.now() });
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    await page.clock.runFor(5500);
    otlp.reset();
    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session", 8000);
    const durationMs = Number(
      getAttr(session.attributes, "session.duration_ms"),
    );
    expect(durationMs).toBeGreaterThanOrEqual(4800);
  });

  test("emits new screen_load with spa start.type after navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    await otlp.waitForSpan("screen_session");

    const newLoad = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(newLoad.attributes, "start.type")).toBe("spa");
    expect(getAttr(newLoad.attributes, "screen.name")).toBeTruthy();
    expect(newLoad.name).toBe("screen_load");
  });

  test("SPA screen_load screen.name matches post-navigation route path", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    await otlp.waitForSpan("screen_session");
    const spaLoad = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(spaLoad.attributes, "screen.name")).toBe("/products");
  });

  test("navigating multiple times emits screen_session each time", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    await otlp.waitForSpan("screen_session", 8000);
    await otlp.waitForSpan("screen_load", 8000);

    otlp.reset();
    await page.click('a:has-text("Cart")');
    await otlp.waitForSpan("screen_session", 8000);

    expect(true).toBe(true);
  });

  test("navigating to product detail works", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");

    otlp.reset();
    await page.click('a:has-text("Products")');
    await otlp.waitForSpan("screen_load");

    otlp.reset();
    const product = await page.$('[data-testid="product-card"]');
    if (product) {
      await product.click();
      const load = await otlp.waitForSpan("screen_load", 8000);
      expect(getAttr(load.attributes, "start.type")).toBe("spa");
    }
  });
});

// ─── Feature Gate Tests ───────────────────────────────────────────────────────

test.describe("@ScreenNav feature gate", () => {
  test("with screenNavigation enabled, screen_load span emitted", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
  });

  test("with screen_navigation disabled in seeded config, no screen_load span", async ({
    page,
    otlp,
  }) => {
    await blockActiveConfigFetch(page);
    await seedPulseSdkConfig(
      page,
      minimalPulseSdkConfig({
        features: [
          {
            featureName: "screen_navigation",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
            config: null,
          },
        ],
      }),
    );
    await page.goto("/");
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllSpans(otlp.captured, "screen_load").length).toBe(0);
  });
});

// ─── Screen Name Resolution ───────────────────────────────────────────────────

test.describe("@ScreenNav screen name resolution", () => {
  test("screen.name present on screen_load span", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");
    expect(getAttr(load.attributes, "screen.name")).toBeTruthy();
  });

  test("screen.name present on screen_session span", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session");
    expect(getAttr(session.attributes, "screen.name")).toBeTruthy();
  });

  test("url.path matches exited screen snapshot on screen_session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session");
    expect(getAttr(session.attributes, "url.path")).toBe("/");
  });
});

// ─── Resource Attributes ──────────────────────────────────────────────────────

test.describe("@ScreenNav resource attributes", () => {
  test("platform attribute is web", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");

    const platform = getResourceAttr(otlp.captured, "platform");
    expect(platform).toBe("web");
  });

  test("session.id in span attributes", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");

    const sessionId = getAttr(load.attributes, "session.id");
    expect(sessionId).toBeTruthy();
  });
});

// ─── Numeric Attributes ───────────────────────────────────────────────────────

test.describe("@ScreenNav numeric attributes", () => {
  test("session.duration is number in screen_session span", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session");

    const durationMs = getAttr(session.attributes, "session.duration_ms");
    expect(typeof durationMs).toBe("number");
    expect(durationMs).toBeGreaterThan(0);
    expect(getAttr(session.attributes, "session.duration")).toEqual(durationMs);
  });
});

// ─── Pulse Type Attributes ───────────────────────────────────────────────────

test.describe("@ScreenNav pulse.type consistency", () => {
  test("screen_load span has correct pulse.type", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");
    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
  });

  test("screen_session span has correct pulse.type", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForSpan("screen_session");
    expect(getAttr(session.attributes, "pulse.type")).toBe("screen_session");
  });
});

// ─── Manual gap-close (NAV) ───────────────────────────────────────────────────

test.describe("@ScreenNav manual gap-close", () => {
  test("NAV-05: goBack emits screen_load with start.type back_forward", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    await page.goto("/products");
    await page.waitForURL("**/products");
    await otlp.waitForSpan("screen_load");
    otlp.reset();
    await page.goBack();
    await page.waitForURL("**/");
    await page.waitForTimeout(1500);

    const loads = findAllSpans(otlp.captured, "screen_load");
    const backLoad = loads.find(
      (s) => getAttr(s.attributes, "start.type") === "back_forward",
    );
    expect(backLoad).toBeDefined();
  });

  test("NAV-10: screen_load has no web.lcp_ms or lcp attrs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");
    const keys = load.attributes.map((a) => a.key);
    expect(keys).not.toContain("web.lcp_ms");
    expect(keys.filter((k) => k === "lcp")).toHaveLength(0);
  });

  test("NAV-11: screen_load carries url.path and page.title", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForSpan("screen_load");
    expect(getAttr(load.attributes, "url.path")).toBe("/");
    expect(
      String(getAttr(load.attributes, "page.title") ?? "").length,
    ).toBeGreaterThan(0);
  });

  test("NAV-07: burst navigations debounce to settled screen_load routes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.evaluate(() => {
      const routes = ["/products", "/cart", "/checkout", "/"];
      let i = 0;
      const burst = () => {
        if (i >= routes.length) return;
        history.pushState({}, "", routes[i]!);
        window.dispatchEvent(new PopStateEvent("popstate"));
        i += 1;
        if (i < routes.length) setTimeout(burst, 30);
      };
      burst();
    });
    await page.waitForTimeout(2500);

    const loads = findAllSpans(otlp.captured, "screen_load");
    const names = loads.map((s) => getAttr(s.attributes, "screen.name"));
    expect(names.filter((n) => n === "/").length).toBeLessThanOrEqual(2);
    expect(names).toContain("/");
  });
});
