/**
 * M3 — Auto click instrumentation (`pulse.type` app.click, OTLP log body app.widget.click).
 * Default rage buffer defers OTLP until tab hide / pagehide flush (Android parity).
 */
import type { Page } from "@playwright/test";
import { test, expect, getAttr, findAllLogs, type OtlpAttr } from "./fixture";
import {
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  blockActiveConfigFetch,
} from "./test-sdk-config";

async function waitForPulseInitialized(page: Page): Promise<void> {
  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const w = window as unknown as {
            Pulse?: { isInitialized: () => boolean };
          };
          return w.Pulse?.isInitialized?.() ?? false;
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

/** Flush `ClickEventBuffer` + log pipeline (matches real tab backgrounding). */
async function flushClickBuffer(page: Page): Promise<void> {
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      get: () => "hidden",
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      get: () => "visible",
      configurable: true,
    });
  });
}

/** Skill D2 floor: OTLP scalars must decode as finite numbers, not loose truthiness. */
function expectFiniteNumberAttr(
  attrs: OtlpAttr[] | undefined,
  key: string,
): void {
  const v = getAttr(attrs, key);
  expect(typeof v).toBe("number");
  expect(Number.isFinite(v)).toBe(true);
}

test.describe("@M3 clicks e2e", () => {
  test("Shop Now link emits app.click with good target and contract attrs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByRole("link", { name: /shop now/i }).click();
    await flushClickBuffer(page);

    const log = await otlp.waitForClickLog(15_000);

    expect(log.body?.stringValue).toBe("app.widget.click");
    expect(getAttr(log.attributes, "pulse.type")).toBe("app.click");
    expect(getAttr(log.attributes, "click.type")).toBe("good");
    expect(getAttr(log.attributes, "app.widget.name")).toBe("A");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.x");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.y");
    expectFiniteNumberAttr(log.attributes, "device.screen.width");
    expectFiniteNumberAttr(log.attributes, "device.screen.height");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.nx");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.ny");
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "screen.name")).toBeTruthy();
  });

  test("click on non-interactive pad emits dead click without widget attrs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(() => {
      const el = document.querySelector("p");
      if (el) el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flushClickBuffer(page);

    const log = await otlp.waitForClickLog(15_000);

    expect(getAttr(log.attributes, "pulse.type")).toBe("app.click");
    expect(getAttr(log.attributes, "click.type")).toBe("dead");
    expect(getAttr(log.attributes, "app.widget.name")).toBeUndefined();
    expect(getAttr(log.attributes, "app.widget.id")).toBeUndefined();
    // Same contract floor as good-click: session + screen stamped by GlobalAttributesProcessor.
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "screen.name")).toBeTruthy();
    // Dead-click coords follow MouseEvent clientX/Y (often 0,0 when unset); still numeric on wire.
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.x");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.y");
    expectFiniteNumberAttr(log.attributes, "device.screen.width");
    expectFiniteNumberAttr(log.attributes, "device.screen.height");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.nx");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.ny");
  });

  test("triple tap on Shop Now yields one rage app.click with click.is_rage", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByRole("link", { name: /shop now/i }).evaluate((el) => {
      const r = el.getBoundingClientRect();
      const x = Math.round(r.left + r.width / 2);
      const y = Math.round(r.top + r.height / 2);
      for (let i = 0; i < 3; i++) {
        el.dispatchEvent(
          new MouseEvent("click", {
            bubbles: true,
            cancelable: true,
            clientX: x,
            clientY: y,
          }),
        );
      }
    });
    await flushClickBuffer(page);

    const clicks = findAllLogs(otlp.captured, "app.click");
    expect(clicks.length).toBe(1);
    const log = clicks[0]!;
    expect(log.body?.stringValue).toBe("app.widget.click");
    expect(getAttr(log.attributes, "pulse.type")).toBe("app.click");
    expect(getAttr(log.attributes, "click.type")).toBe("good");
    expect(getAttr(log.attributes, "click.is_rage")).toBe(true);
    const rc = getAttr(log.attributes, "click.rage_count");
    expect(typeof rc).toBe("number");
    expect(rc as number).toBeGreaterThanOrEqual(3);
    expect(getAttr(log.attributes, "app.widget.name")).toBe("A");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.x");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.y");
    expectFiniteNumberAttr(log.attributes, "device.screen.width");
    expectFiniteNumberAttr(log.attributes, "device.screen.height");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.nx");
    expectFiniteNumberAttr(log.attributes, "app.screen.coordinate.ny");
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "screen.name")).toBeTruthy();
  });

  test("does not emit app.click when click feature gate is disabled", async ({
    page,
    otlp,
  }) => {
    await seedPulseSdkConfig(
      page,
      minimalPulseSdkConfig({
        features: [
          {
            featureName: "click",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
          },
        ],
      }),
    );
    await blockActiveConfigFetch(page);

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByRole("link", { name: /shop now/i }).click();
    await flushClickBuffer(page);
    await page.waitForTimeout(400);

    expect(findAllLogs(otlp.captured, "app.click")).toHaveLength(0);
  });
});
