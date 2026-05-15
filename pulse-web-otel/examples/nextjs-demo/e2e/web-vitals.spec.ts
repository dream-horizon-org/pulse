/**
 * Web vitals + navigation_id — mirrors ecommerce-demo assertions for Next.js App Router.
 */
import { test, expect, findAllLogs, findAllSpans, getAttr } from "./fixture";

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
  const ctx = getAttr(attrs, "web_vital.context");
  if (ctx !== undefined) {
    expect(["pageload", "navigation"]).toContain(ctx);
  }
  const delta = getAttr(attrs, "web_vital.delta");
  if (delta !== undefined) {
    expect(Number.isFinite(delta as number)).toBe(true);
  }
}

test.describe("@WebVitals (Next.js demo)", () => {
  test("emits TTFB web_vital with navigation_id after load", async ({
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
    assertExportedWebVitalAttrs(ttfb!.attributes);
  });

  test("SPA screen_load span carries navigation_id after App Router navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(800);

    const beforeCount = findAllSpans(otlp.captured, "screen_load").length;

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    const deadline = Date.now() + 12_000;
    let loads = findAllSpans(otlp.captured, "screen_load");
    while (loads.length <= beforeCount && Date.now() < deadline) {
      await page.waitForTimeout(200);
      loads = findAllSpans(otlp.captured, "screen_load");
    }

    expect(loads.length).toBeGreaterThan(beforeCount);
    const lastLoad = loads[loads.length - 1]!;
    const spanNavId = getAttr(lastLoad.attributes, "navigation_id");
    expect(typeof spanNavId).toBe("string");
    expect((spanNavId as string).length).toBeGreaterThan(10);
  });
});
