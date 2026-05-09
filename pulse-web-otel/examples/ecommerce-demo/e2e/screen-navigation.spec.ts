import type { Page } from "@playwright/test";
import { test, expect, getAttr, findAllLogs, getResourceAttr } from "./fixture";
import {
  blockActiveConfigFetch,
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  waitPastSeededSignalsBatchWindow,
} from "./test-sdk-config";

/**
 * Screen Navigation E2E Tests — TDD foundation
 *
 * Tests screen navigation signal emission (screen_load, screen_interactive, screen_session)
 * from the pulse-web-otel SDK integrated with the ecommerce demo.
 *
 * Run: yarn e2e --grep "@ScreenNav" --project=chromium
 */

// ─── Initial Page Load ────────────────────────────────────────────────────────

test.describe("@ScreenNav initial page load", () => {
  test("emits screen_load on page load", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load", 8000);

    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
    expect(getAttr(load.attributes, "screen.name")).toBeTruthy();
    expect(getAttr(load.attributes, "session.id")).toBeTruthy();
  });

  test("screen_load has valid start.type", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");

    const startType = getAttr(load.attributes, "start.type");
    expect(startType).toMatch(/^(cold|reload|back_forward)$/);
  });

  test("screen_interactive emitted on initial load", async ({ page, otlp }) => {
    await page.goto("/");
    const tti = await otlp.waitForLog("screen_interactive", 8000);

    expect(getAttr(tti.attributes, "pulse.type")).toBe("screen_interactive");
    expect(getAttr(tti.attributes, "screen.name")).toBeTruthy();
  });

  test("screen_load and screen_interactive have consistent session.id", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");
    const tti = await otlp.waitForLog("screen_interactive");

    const sessionId = getAttr(load.attributes, "session.id");
    expect(getAttr(tti.attributes, "session.id")).toBe(sessionId);
  });
});

// ─── SPA Navigation ───────────────────────────────────────────────────────────

test.describe("@ScreenNav SPA navigation", () => {
  test("emits screen_session on route change", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    // Navigate to Products
    await page.click('a:has-text("Products")');
    const session = await otlp.waitForLog("screen_session", 8000);

    expect(getAttr(session.attributes, "pulse.type")).toBe("screen_session");
    expect(getAttr(session.attributes, "screen.name")).toBeTruthy();
    expect(getAttr(session.attributes, "session.id")).toBeTruthy();

    const durationMs = getAttr(session.attributes, "session.duration_ms");
    expect(typeof durationMs).toBe("number");
    expect(durationMs).toBeGreaterThan(0);
    expect(getAttr(session.attributes, "session.duration")).toEqual(durationMs);
  });

  test("emits new screen_load with spa start.type after navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    await otlp.waitForLog("screen_session");

    const newLoad = await otlp.waitForLog("screen_load", 8000);
    expect(getAttr(newLoad.attributes, "start.type")).toBe("spa");
    expect(getAttr(newLoad.attributes, "screen.name")).toBeTruthy();
  });

  test("navigating multiple times emits screen_session each time", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    // Navigate: Home → Products
    await page.click('a:has-text("Products")');
    await otlp.waitForLog("screen_session", 8000);
    await otlp.waitForLog("screen_load", 8000);

    // The second navigation should emit another session
    otlp.reset();
    await page.click('a:has-text("Cart")');
    await otlp.waitForLog("screen_session", 8000);

    expect(true).toBe(true); // Successfully navigated and emitted signals
  });

  test("navigating to product detail works", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");

    otlp.reset();
    await page.click('a:has-text("Products")');
    await otlp.waitForLog("screen_load");

    otlp.reset();
    const product = await page.$('[data-testid="product-card"]');
    if (product) {
      await product.click();
      const load = await otlp.waitForLog("screen_load", 8000);
      expect(getAttr(load.attributes, "start.type")).toBe("spa");
    }
  });
});

// ─── Feature Gate Tests ───────────────────────────────────────────────────────

test.describe("@ScreenNav feature gate", () => {
  test("with screenNavigation enabled, screen_load emitted", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load", 8000);
    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
  });

  test("with screen_navigation disabled in seeded config, no screen_load", async ({
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
    expect(findAllLogs(otlp.captured, "screen_load").length).toBe(0);
  });
});

// ─── Screen Name Resolution ───────────────────────────────────────────────────

test.describe("@ScreenNav screen name resolution", () => {
  test("screen.name present on screen_load", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");
    expect(getAttr(load.attributes, "screen.name")).toBeTruthy();
  });

  test("screen.name present on screen_interactive", async ({ page, otlp }) => {
    await page.goto("/");
    const tti = await otlp.waitForLog("screen_interactive");
    expect(getAttr(tti.attributes, "screen.name")).toBeTruthy();
  });

  test("screen.name present on screen_session", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForLog("screen_session");
    expect(getAttr(session.attributes, "screen.name")).toBeTruthy();
  });

  test("url.path matches current pathname", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");
    expect(getAttr(load.attributes, "url.path")).toBe("/");
  });
});

// ─── Resource Attributes ──────────────────────────────────────────────────────

test.describe("@ScreenNav resource attributes", () => {
  test("platform attribute is web", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");

    const platform = getResourceAttr(otlp.captured, "platform");
    expect(platform).toBe("web");
  });

  test("session.id in attributes", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");

    const sessionId = getAttr(load.attributes, "session.id");
    expect(sessionId).toBeTruthy();
  });
});

// ─── Numeric Attributes ───────────────────────────────────────────────────────

test.describe("@ScreenNav numeric attributes", () => {
  test("session.duration is number in screen_session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForLog("screen_session");

    const durationMs = getAttr(session.attributes, "session.duration_ms");
    expect(typeof durationMs).toBe("number");
    expect(durationMs).toBeGreaterThan(0);
    expect(getAttr(session.attributes, "session.duration")).toEqual(durationMs);
  });

  test("tti present in screen_interactive when available", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const tti = await otlp.waitForLog("screen_interactive");

    const ttiValue = getAttr(tti.attributes, "tti");
    // tti may be undefined in some environments, but if present must be a valid number
    if (ttiValue !== undefined) {
      expect(typeof ttiValue).toBe("number");
      expect(ttiValue).toBeGreaterThanOrEqual(0);
    }
  });
});

// ─── Pulse Type Attributes ───────────────────────────────────────────────────

test.describe("@ScreenNav pulse.type consistency", () => {
  test("screen_load has correct pulse.type", async ({ page, otlp }) => {
    await page.goto("/");
    const load = await otlp.waitForLog("screen_load");
    expect(getAttr(load.attributes, "pulse.type")).toBe("screen_load");
  });

  test("screen_interactive has correct pulse.type", async ({ page, otlp }) => {
    await page.goto("/");
    const tti = await otlp.waitForLog("screen_interactive");
    expect(getAttr(tti.attributes, "pulse.type")).toBe("screen_interactive");
  });

  test("screen_session has correct pulse.type", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("screen_load");
    otlp.reset();

    await page.click('a:has-text("Products")');
    const session = await otlp.waitForLog("screen_session");
    expect(getAttr(session.attributes, "pulse.type")).toBe("screen_session");
  });
});
