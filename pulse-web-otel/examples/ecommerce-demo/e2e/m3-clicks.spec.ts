/**
 * M3 — Auto click instrumentation (`pulse.type` app.click, OTLP log body app.widget.click).
 */
import type { Page } from "@playwright/test";
import { test, expect, getAttr } from "./fixture";

async function waitForPulseWebInitialized(page: Page): Promise<void> {
  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const w = window as unknown as {
            PulseWeb?: { isInitialized: () => boolean };
          };
          return w.PulseWeb?.isInitialized?.() ?? false;
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

test.describe("@M3 clicks e2e", () => {
  test("Shop Now link emits app.click with good target and contract attrs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.getByRole("link", { name: /shop now/i }).click();

    const log = await otlp.waitForClickLog(15_000);

    expect(log.body?.stringValue).toBe("app.widget.click");
    expect(getAttr(log.attributes, "pulse.type")).toBe("app.click");
    expect(getAttr(log.attributes, "click.type")).toBe("good");
    expect(getAttr(log.attributes, "app.widget.name")).toBe("A");
    expect(getAttr(log.attributes, "app.screen.coordinate.x")).toBeDefined();
    expect(getAttr(log.attributes, "app.screen.coordinate.y")).toBeDefined();
    expect(getAttr(log.attributes, "device.screen.width")).toBeDefined();
    expect(getAttr(log.attributes, "device.screen.height")).toBeDefined();
    expect(getAttr(log.attributes, "app.screen.coordinate.nx")).toBeDefined();
    expect(getAttr(log.attributes, "app.screen.coordinate.ny")).toBeDefined();
  });

  test("click on non-interactive pad emits dead click without widget attrs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(() => {
      const el = document.querySelector("p");
      if (el) el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    const log = await otlp.waitForClickLog(15_000);

    expect(getAttr(log.attributes, "pulse.type")).toBe("app.click");
    expect(getAttr(log.attributes, "click.type")).toBe("dead");
    expect(getAttr(log.attributes, "app.widget.name")).toBeUndefined();
    expect(getAttr(log.attributes, "app.widget.id")).toBeUndefined();
  });
});
