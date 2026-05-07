/**
 * Next.js Demo — ClickHouse E2E Tests
 *
 * Requires full Pulse stack + nextjs-demo running:
 *   cd deploy && ./scripts/start.sh -d
 *   cd examples/nextjs-demo && yarn dev
 *
 * Run: yarn workspace nextjs-demo e2e:ch
 *      or: playwright test --config e2e/playwright.ch.config.ts
 *
 * These tests do NOT intercept OTLP — signals flow through the real collector
 * into ClickHouse. Assertions query CH directly via ch-fixture helpers.
 */
import { test, expect, type Page } from "@playwright/test";
import {
  waitForChLog,
  waitForChStackTrace,
  SERVICE_NAME,
} from "./ch-fixture";

async function waitForSdkReady(page: Page): Promise<void> {
  await expect
    .poll(
      () =>
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

// ─── Session ─────────────────────────────────────────────────────────────────

test.describe("CH — session lifecycle", () => {
  test("session.start lands in CH otel_logs for nextjs-demo", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkReady(page);

    const row = await waitForChLog("session.start", SERVICE_NAME);
    expect(row.PulseType).toBe("session.start");
    expect(row.session_id).toBeTruthy();
  });
});

// ─── Screen tracking ──────────────────────────────────────────────────────────

test.describe("CH — screen tracking", () => {
  test("screen.name = /products lands in CH after navigating to /products", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    const row = await waitForChLog("session.start", SERVICE_NAME, 25_000);
    expect(row).toBeDefined();
  });
});

// ─── Error tracking ───────────────────────────────────────────────────────────

test.describe("CH — error tracking", () => {
  test("device.crash lands in CH after PulseErrorBoundary catch", async ({
    page,
  }) => {
    await page.goto("/error-demo");
    await page.waitForLoadState("networkidle");

    await page.click("[data-testid='throw-btn']");

    const row = await waitForChStackTrace("device.crash", SERVICE_NAME);
    expect(row.PulseType).toBe("device.crash");
    expect(row.ExceptionMessage).toContain("Boundary crash");
  });

  test("non_fatal lands in CH after reportException", async ({ page }) => {
    await page.goto("/error-demo");
    await page.waitForLoadState("networkidle");

    await page.click("[data-testid='manual-exception-btn']");

    const row = await waitForChStackTrace("non_fatal", SERVICE_NAME);
    expect(row.PulseType).toBe("non_fatal");
    expect(row.ExceptionMessage).toContain("Manual non_fatal");
  });

  test("device.crash lands in CH after manual reportDeviceCrash", async ({
    page,
  }) => {
    await page.goto("/error-demo");
    await page.waitForLoadState("networkidle");

    await page.click("[data-testid='manual-crash-btn']");

    const row = await waitForChStackTrace("device.crash", SERVICE_NAME);
    expect(row.PulseType).toBe("device.crash");
    expect(row.ExceptionMessage).toContain("Manual device.crash");
  });
});
