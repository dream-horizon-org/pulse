import type { Page } from "@playwright/test";
import { test, expect, getAttr } from "./fixture";

/**
 * E2E — percent-encoded pathnames and `/screens/<embedded>` unwrapping for screen.name.
 *
 * Run: yarn e2e --grep "@ScreenPathname" --project=chromium
 */

const EMBEDDED_WRAPPER_PATH =
  "/projects/default-project/screens/%2Fprojects%2Fdefault-project%2Finteraction-details%2FUI%2520Session%2520Replay%2520Open";
const EXPECTED_EMBEDDED =
  "/projects/default-project/interaction-details/UI Session Replay Open";

const ENCODED_DIRECT_PATH =
  "/projects/default-project/interaction-details/UI%20Onboarding%20Success%20to%20Dashboard";
const EXPECTED_DIRECT =
  "/projects/default-project/interaction-details/UI Onboarding Success to Dashboard";

async function waitForPulseInitialized(page: Page): Promise<void> {
  await page.waitForFunction(
    () =>
      Boolean(
        (window as unknown as { Pulse?: { trackEvent?: unknown } }).Pulse
          ?.trackEvent,
      ),
    undefined,
    { timeout: 15_000 },
  );
}

async function trackScreenNameCheck(page: Page, body: string): Promise<void> {
  await page.evaluate((eventBody) => {
    (
      window as unknown as { Pulse: { trackEvent: (n: string) => void } }
    ).Pulse.trackEvent(eventBody);
  }, body);
}

test.describe("@ScreenPathname encoded pathnames", () => {
  test("cold screen_load unwraps /screens/ embedded analytics path", async ({
    page,
    otlp,
  }) => {
    await page.goto(EMBEDDED_WRAPPER_PATH);
    const load = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(load.attributes, "screen.name")).toBe(EXPECTED_EMBEDDED);
  });

  test("custom event after cold load carries unwrapped screen.name", async ({
    page,
    otlp,
  }) => {
    await page.goto(EMBEDDED_WRAPPER_PATH);
    await waitForPulseInitialized(page);
    await trackScreenNameCheck(page, "embedded_screen_name_check");
    const log = await otlp.waitForLogByBody("embedded_screen_name_check");
    expect(getAttr(log.attributes, "screen.name")).toBe(EXPECTED_EMBEDDED);
  });

  test("cold screen_load decodes percent-encoded spaces on direct routes", async ({
    page,
    otlp,
  }) => {
    await page.goto(ENCODED_DIRECT_PATH);
    const load = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(load.attributes, "screen.name")).toBe(EXPECTED_DIRECT);
  });

  test("custom event on direct encoded route carries decoded screen.name", async ({
    page,
    otlp,
  }) => {
    await page.goto(ENCODED_DIRECT_PATH);
    await waitForPulseInitialized(page);
    await trackScreenNameCheck(page, "encoded_direct_screen_name_check");
    const log = await otlp.waitForLogByBody("encoded_direct_screen_name_check");
    expect(getAttr(log.attributes, "screen.name")).toBe(EXPECTED_DIRECT);
  });
});

test.describe("@ScreenPathname SPA history navigation", () => {
  test("history.pushState to embedded path updates screen.name on next signal", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    otlp.reset();

    await page.evaluate((path) => {
      history.pushState({}, "", path);
    }, EMBEDDED_WRAPPER_PATH);

    await page.waitForTimeout(300);
    await trackScreenNameCheck(page, "spa_embedded_screen_name_check");
    const log = await otlp.waitForLogByBody("spa_embedded_screen_name_check");
    expect(getAttr(log.attributes, "screen.name")).toBe(EXPECTED_EMBEDDED);
  });

  test("SPA screen_load after pushState to embedded path has unwrapped screen.name", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForSpan("screen_load");
    otlp.reset();

    await page.evaluate((path) => {
      history.pushState({}, "", path);
    }, EMBEDDED_WRAPPER_PATH);

    const spaLoad = await otlp.waitForSpan("screen_load", 8000);
    expect(getAttr(spaLoad.attributes, "screen.name")).toBe(EXPECTED_EMBEDDED);
    expect(getAttr(spaLoad.attributes, "start.type")).toBe("spa");
  });
});

test.describe("@ScreenPathname negative cases", () => {
  test("/screens/slug without absolute path is not unwrapped", async ({
    page,
    otlp,
  }) => {
    await page.goto("/demo/screens/home");
    await waitForPulseInitialized(page);
    await trackScreenNameCheck(page, "screens_slug_not_unwrapped");
    const log = await otlp.waitForLogByBody("screens_slug_not_unwrapped");
    expect(getAttr(log.attributes, "screen.name")).toBe("/demo/screens/home");
  });
});
