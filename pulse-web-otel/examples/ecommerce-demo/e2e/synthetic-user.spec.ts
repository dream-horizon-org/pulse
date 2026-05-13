/**
 * Long synthetic journey — not part of `yarn e2e:web-sdk-gates`.
 * Optional Web Vitals stress via SYNTHETIC_STRESS=1 + URL params (see demo README).
 *
 * **Real ClickHouse ingest:** `yarn e2e:synthetic:ingest` (sets `E2E_REAL_OTLP=1`). Start the stack
 * with `pulse-otel-collector` on :4318 first. OTLP is not intercepted — `waitForLog` only waits
 * for flush timing (see `e2e/fixture.ts`). Do not use `E2E_REAL_OTLP` with gate specs.
 */
import type { Page } from "@playwright/test";
import { test, expect } from "./fixture";

const ITERATIONS = Math.max(
  1,
  Number.parseInt(process.env.SYNTHETIC_ITERATIONS ?? "5", 10) || 5,
);
const CLEAR_EVERY = Math.max(
  1,
  Number.parseInt(process.env.SYNTHETIC_CLEAR_STORAGE_EVERY ?? "2", 10) || 2,
);
const STRESS = process.env.SYNTHETIC_STRESS === "1";

test.describe.configure({
  mode: "serial",
  /** Default Playwright test timeout (30s) is too low for ×N full journeys. */
  timeout: Math.min(900_000, Math.max(120_000, ITERATIONS * 55_000)),
});

function withStressPath(path: string, iteration: number): string {
  if (!STRESS) return path;
  const base = path.startsWith("/") ? path : `/${path}`;
  const u = new URL(base, "http://localhost");
  u.searchParams.set("pulse_wv_stress", "all");
  u.searchParams.set("pulse_wv_stress_seed", String(iteration));
  u.searchParams.set("pulse_wv_stress_p", "1");
  const qs = u.searchParams.toString();
  return qs ? `${u.pathname}?${qs}` : u.pathname;
}

function stressPaintGateMayShow(url: string): boolean {
  try {
    const u = new URL(url);
    const mode = u.searchParams.get("pulse_wv_stress");
    return (
      mode != null &&
      mode !== "" &&
      mode.toLowerCase() !== "off" &&
      (mode.toLowerCase() === "lcp" ||
        mode.toLowerCase() === "fcp" ||
        mode.toLowerCase() === "all")
    );
  } catch {
    return false;
  }
}

async function settleRoute(page: Page): Promise<void> {
  if (!stressPaintGateMayShow(page.url())) {
    await page.waitForTimeout(150);
    return;
  }
  const gate = page.locator('[data-testid="wv-stress-paint-gate"]');
  await expect
    .poll(
      async () => {
        const n = await gate.count();
        if (n === 0) return true;
        return !(await gate.isVisible());
      },
      { timeout: 20_000, intervals: [50, 100, 200, 400] },
    )
    .toBe(true);
  await page.waitForTimeout(250);
}

test.describe("@SyntheticUser", () => {
  test(`full app journey ×${ITERATIONS}`, async ({ page, context, otlp }) => {
    for (let i = 0; i < ITERATIONS; i += 1) {
      if (i > 0 && i % CLEAR_EVERY === 0) {
        await context.clearCookies();
        await page.goto("/");
        await page.evaluate(() => {
          try {
            localStorage.clear();
            sessionStorage.clear();
          } catch {
            /* ignore */
          }
        });
      }

      await page.goto(withStressPath("/", i));
      await otlp.waitForLog("session.start", 20_000);
      await settleRoute(page);

      await expect(page.getByText("Welcome to PulseStore")).toBeVisible();
      await page.getByRole("link", { name: /Shop Now/ }).click();
      await settleRoute(page);
      await expect(
        page.getByRole("heading", { name: "All Products" }),
      ).toBeVisible();

      await page.goto(withStressPath("/products", i));
      await settleRoute(page);
      await expect(
        page.getByRole("heading", { name: "All Products" }),
      ).toBeVisible();
      await expect(page.getByTestId("product-card").first()).toBeVisible();

      const rage = page.getByTestId("rage-click-button");
      for (let c = 0; c < 5; c += 1) {
        await rage.click();
      }
      await page.waitForTimeout(200);

      await page.getByTestId("product-add-to-cart").first().click();
      await page.waitForTimeout(350);

      await page.goto(withStressPath("/products/1", i));
      await settleRoute(page);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible({
        timeout: 10_000,
      });

      await page.goto(withStressPath("/cart", i));
      await settleRoute(page);
      await expect(
        page
          .getByRole("heading", { name: "Your Cart" })
          .or(page.getByText("Your cart is empty")),
      ).toBeVisible();

      const removeBtn = page.getByRole("button", { name: "Remove" });
      if ((await removeBtn.count()) > 0) {
        await removeBtn.first().click();
        await page.waitForTimeout(200);
        await page.goto(withStressPath("/products", i));
        await settleRoute(page);
        await page.getByTestId("product-add-to-cart").first().click();
        await page.waitForTimeout(250);
        await page.goto(withStressPath("/cart", i));
        await settleRoute(page);
      }

      await page.goto(withStressPath("/checkout", i));
      await settleRoute(page);
      await expect(page.getByRole("heading", { name: "Checkout" })).toBeVisible(
        {
          timeout: 10_000,
        },
      );
      await page.getByTestId("checkout-step-1-next").click();
      await page.waitForTimeout(200);
      await page.getByTestId("checkout-step-2-next").click();
      await page.waitForTimeout(200);
      await page.getByTestId("checkout-step-3-confirm").click();
      await page.waitForTimeout(450);

      await page.goto(withStressPath("/network-lab", i));
      await settleRoute(page);
      await page.getByTestId("network-lab-fetch-get-local").click();
      await expect
        .poll(
          async () => {
            const row = page
              .locator("div")
              .filter({ hasText: "Fetch GET local JSON" })
              .first();
            const t = await row.textContent();
            return Boolean(t?.includes("ok") && t?.includes("status"));
          },
          { timeout: 15_000, intervals: [100, 250, 500] },
        )
        .toBe(true);

      await page.goto(withStressPath("/error-demo", i));
      await settleRoute(page);
      await page.getByTestId("throw-render-error").click();
      await expect(page.getByText("Render error caught")).toBeVisible({
        timeout: 10_000,
      });
      await page.getByRole("button", { name: "Home" }).click();
      await settleRoute(page);
      await expect(page.getByText("Welcome to PulseStore")).toBeVisible();

      await page.goto(withStressPath("/products", i));
      await settleRoute(page);
      await page.getByRole("link", { name: "Cart", exact: true }).click();
      await settleRoute(page);
      await page.goBack();
      await settleRoute(page);
      try {
        await page.goForward({ timeout: 3000 });
        await settleRoute(page);
      } catch {
        /* history stack may not forward */
      }

      await page.reload();
      await otlp.waitForLog("session.start", 20_000);
      await settleRoute(page);
    }
  });
});
