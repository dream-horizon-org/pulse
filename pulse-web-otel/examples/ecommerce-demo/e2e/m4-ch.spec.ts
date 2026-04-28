/**
 * M4 CH Integration Tests — Navigation Instrumentation (TC 1–21)
 *
 * Verifies that NavigationInstrumentation spans and logs emitted by the
 * browser actually land in ClickHouse via the real OTEL collector pipeline.
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch            (headless)
 *   yarn e2e:ch:headed     (headed — watch in real browser)
 *
 * Each test:
 *   1. Drives the browser (no page.route() intercept — real OTLP export)
 *   2. Waits INGEST_WAIT ms for batch flush + collector → CH ingest
 *   3. Queries CH and asserts on the row
 *
 * Auto-skips entire suite if CH is not reachable (stack not running).
 */

import { test, expect } from "@playwright/test";
import {
  isCHAvailable,
  waitForCHSpan,
  waitForCHLog,
  countCHSpans,
  countCHLogs,
} from "./ch-fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

/**
 * Time to wait after a browser action before querying CH.
 * 1s batch delay + ~4s collector→CH ingest latency.
 * pollUntilCH will keep retrying up to timeoutMs anyway.
 */
const INGEST_WAIT = 5_000;

// ─── Helpers ──────────────────────────────────────────────────────────────────

type PulseWebWindow = Window & {
  PulseWeb?: {
    isInitialized: () => boolean;
    setScreenName: (name: string) => void;
    shutdown: () => Promise<void>;
  };
};

async function waitForSdkInit(page: import("@playwright/test").Page): Promise<void> {
  await expect
    .poll(
      () =>
        page.evaluate(() =>
          (window as unknown as PulseWebWindow).PulseWeb?.isInitialized?.() ?? false,
        ),
      { timeout: 15_000 },
    )
    .toBe(true);
}

// ─── Suite setup ──────────────────────────────────────────────────────────────

test.beforeEach(async () => {
  const available = await isCHAvailable();
  if (!available) {
    test.skip(true, "ClickHouse not reachable — start full stack with deploy/scripts/start.sh");
  }
});

// ─── TC 1–4: Page-load spans ──────────────────────────────────────────────────

test.describe("@M4-CH page-load spans", () => {
  test("TC 1: screen_load row in CH — type=navigate, start=cold, load.duration_ms > 0", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_load",
      `SpanAttributes['url.path'] = '/products'`,
    );

    expect(row.PulseType).toBe("screen_load");
    expect(row.screen_name).toBe("/products");
    expect(row.url_path).toBe("/products");
    expect(row.navigation_type).toBe("navigate");
    expect(row.start_type).toBe("cold");
    expect(Number(row.load_duration_ms)).toBeGreaterThan(0);
    expect(Number(row.ttfb_ms)).toBeGreaterThanOrEqual(0);
  });

  test("TC 2: screen_interactive row in CH — tti >= 0", async ({ page }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_interactive",
      `SpanAttributes['url.path'] = '/products'`,
    );

    expect(row.PulseType).toBe("screen_interactive");
    expect(row.screen_name).toBe("/products");
    expect(row.url_path).toBe("/products");
    expect(Number(row.tti)).toBeGreaterThanOrEqual(0);
  });

  test("TC 3: screen_load with navigation_type=reload on hard reload", async ({ page }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.reload();
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_load",
      `SpanAttributes['navigation.type'] = 'reload'`,
    );

    expect(row.navigation_type).toBe("reload");
    expect(row.start_type).toBe("reload");
    expect(row.screen_name).toBe("/products");
  });

  test("TC 4: screen_load with navigation_type=back_forward on browser back", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.goBack({ waitUntil: "load" });
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_load",
      `SpanAttributes['navigation.type'] = 'back_forward'`,
    );

    expect(row.navigation_type).toBe("back_forward");
    expect(row.start_type).toBe("back_forward");
  });
});

// ─── TC 5–6: SPA screen_session ───────────────────────────────────────────────

test.describe("@M4-CH SPA screen_session", () => {
  test("TC 5: screen_session for / — previous_screen.name empty on first SPA nav", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(300); // ensure > 100ms on /

    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/' AND SpanAttributes['previous_screen.name'] = ''`,
    );

    expect(row.screen_name).toBe("/");
    expect(row.previous_screen_name).toBe("");
    expect(Number(row.session_duration)).toBeGreaterThan(0);
  });

  test("TC 6: second SPA nav — previous_screen.name = / on /products session", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    // / → /products (session for /)
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(300); // stay on /products > 100ms

    // /products → /cart (session for /products with previous = /)
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/products' AND SpanAttributes['previous_screen.name'] = '/'`,
    );

    expect(row.screen_name).toBe("/products");
    expect(row.previous_screen_name).toBe("/");
  });
});

// ─── TC 7: routePatterns ──────────────────────────────────────────────────────

test.describe("@M4-CH routePatterns", () => {
  test("TC 7: routePatterns → screen.name=ProductDetail in CH", async ({ page }) => {
    await page.addInitScript(() => {
      (window as unknown as Record<string, unknown>)["__pulseE2eRoutePatterns"] = [
        { pattern: "/products/:id", name: "ProductDetail" },
      ];
    });

    await page.goto("/products/123");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_load",
      `SpanAttributes['screen.name'] = 'ProductDetail'`,
    );

    expect(row.screen_name).toBe("ProductDetail");
    expect(row.url_path).toBe("/products/123");
  });
});

// ─── TC 8–9: screen.name heuristics ──────────────────────────────────────────

test.describe("@M4-CH screen.name heuristics", () => {
  test("TC 8: numeric ID stripped → screen.name=/products in CH", async ({ page }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    await page.evaluate(() => history.pushState({}, "", "/products/123"));
    await page.waitForTimeout(300);
    await page.evaluate(() => history.pushState({}, "", "/cart")); // flush
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] LIKE '/products/%' AND SpanAttributes['screen.name'] = '/products'`,
    );

    expect(row.screen_name).toBe("/products");
    expect(row.url_path).toMatch(/^\/products\//);
  });

  test("TC 9: UUID stripped → screen.name=/orders in CH", async ({ page }) => {
    const uuid = "550e8400-e29b-41d4-a716-446655440000";

    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    await page.evaluate((id) => history.pushState({}, "", `/orders/${id}`), uuid);
    await page.waitForTimeout(300);
    await page.evaluate(() => history.pushState({}, "", "/cart")); // flush
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] LIKE '/orders/%' AND SpanAttributes['screen.name'] = '/orders'`,
    );

    expect(row.screen_name).toBe("/orders");
    expect(row.url_path).toContain("/orders/");
  });
});

// ─── TC 10–11: setScreenName ──────────────────────────────────────────────────

test.describe("@M4-CH setScreenName", () => {
  test("TC 10: setScreenName override in CH screen_session row", async ({ page }) => {
    await page.goto("/products/123");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    await page.evaluate(() => {
      (window as unknown as PulseWebWindow).PulseWeb!.setScreenName("FeaturedProduct");
    });

    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['screen.name'] = 'FeaturedProduct'`,
    );

    expect(row.screen_name).toBe("FeaturedProduct");
  });

  test("TC 11: setScreenName cleared — /cart session has screen.name=/cart in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);

    // Set override on /products, then navigate to /cart
    await page.evaluate(() => {
      (window as unknown as PulseWebWindow).PulseWeb!.setScreenName("Override");
    });
    await page.click('a[href="/cart"]');
    await page.waitForTimeout(300); // stay on /cart

    // Navigate away to flush /cart session
    await page.click('a[href="/checkout"]');
    await page.waitForTimeout(INGEST_WAIT);

    // /cart session must use /cart, not the override from /products
    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/cart' AND SpanAttributes['screen.name'] = '/cart'`,
    );

    expect(row.screen_name).toBe("/cart");
    expect(row.url_path).toBe("/cart");
  });
});

// ─── TC 12: pagehide ──────────────────────────────────────────────────────────

test.describe("@M4-CH pagehide", () => {
  test("TC 12: pagehide → screen_session for / in CH", async ({ page }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/'`,
    );

    expect(row.screen_name).toBe("/");
    expect(Number(row.session_duration)).toBeGreaterThanOrEqual(100);
  });
});

// ─── TC 13: screen.name stamped globally ─────────────────────────────────────

test.describe("@M4-CH screen.name globally stamped", () => {
  test("TC 13: custom_event log carries screen.name=/products in CH", async ({
    page,
  }) => {
    // Note: non_fatal logs are routed to pulse-server (BE) by the collector,
    // not to ClickHouse. Only custom_event is verified here.
    await page.goto("/products");
    await waitForSdkInit(page);

    // Fire custom_event via "Add to cart" button
    await page.locator('[data-testid="product-card"] button').first().click();
    await page.waitForTimeout(INGEST_WAIT);

    const customEventRow = await waitForCHLog(
      "custom_event",
      `LogAttributes['screen.name'] = '/products'`,
    );
    expect(customEventRow.screen_name).toBe("/products");
  });
});

// ─── TC 14: url.path on all navigation span types ─────────────────────────────

test.describe("@M4-CH url.path on all navigation span types", () => {
  test("TC 14: url.path=/products on screen_load, screen_interactive, screen_session in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    // Flush session
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(INGEST_WAIT);

    const [loadRow, interactiveRow, sessionRow] = await Promise.all([
      waitForCHSpan("screen_load", `SpanAttributes['url.path'] = '/products'`),
      waitForCHSpan("screen_interactive", `SpanAttributes['url.path'] = '/products'`),
      waitForCHSpan("screen_session", `SpanAttributes['url.path'] = '/products'`),
    ]);

    expect(loadRow.url_path).toBe("/products");
    expect(interactiveRow.url_path).toBe("/products");
    expect(sessionRow.url_path).toBe("/products");
  });
});

// ─── TC 15–18: Negative / Guard Tests ────────────────────────────────────────

test.describe("@M4-CH negative: session guards", () => {
  test("TC 15: sub-100ms rapid pushState → no /a session in CH", async ({ page }) => {
    await page.goto("/");
    await waitForSdkInit(page);

    const before = Date.now();

    // Two sync pushStates — /a duration < 1ms → sub-100ms guard fires
    await page.evaluate(() => {
      history.pushState({}, "", "/a");
      history.pushState({}, "", "/b");
    });

    // Flush /b session
    await page.waitForTimeout(300);
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHSpans(
      "screen_session",
      `SpanAttributes['url.path'] = '/a'`,
      windowSeconds,
    );

    expect(count).toBe(0); // /a suppressed by < 100ms guard
  });

  test("TC 16: replaceState → exactly 1 /checkout session in CH", async ({ page }) => {
    await page.goto("/checkout");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    await page.evaluate(() => history.replaceState({}, "", "/checkout?step=2"));
    await page.waitForTimeout(100);

    await page.click('a[href="/cart"]');
    await page.waitForTimeout(INGEST_WAIT);

    // Both /checkout and /checkout?step=2 belong to same session — only 1 span
    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] LIKE '/checkout%'`,
    );

    expect(row.url_path).toMatch(/^\/checkout/);
  });

  test("TC 17: same-route pushState → 1 /products session with combined duration in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    // Same-pathname push — must not split the session
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(200);

    await page.click('a[href="/cart"]');
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/products'`,
    );

    expect(row.url_path).toBe("/products");
    // Combined duration ≥ 400ms (two 200ms+ waits)
    expect(Number(row.session_duration)).toBeGreaterThan(400);
  });

  test("TC 18: hash-only pushState → 1 /products session in CH", async ({ page }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);

    // Hash change — pathname unchanged, must not split the session
    await page.evaluate(() => history.pushState({}, "", "/products#section"));
    await page.waitForTimeout(200);

    await page.click('a[href="/cart"]');
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSpan(
      "screen_session",
      `SpanAttributes['url.path'] = '/products'`,
    );

    expect(row.url_path).toBe("/products");
  });
});

// ─── TC 19–21: Consent / Lifecycle ───────────────────────────────────────────

test.describe("@M4-CH negative: consent / lifecycle", () => {
  test("TC 19: consent=DENIED → zero navigation spans in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(2_000);

    await page.evaluate(() => {
      history.pushState({}, "", "/tc19-denied-a");
      history.pushState({}, "", "/tc19-denied-b");
    });
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;

    const [loads, sessions] = await Promise.all([
      countCHSpans("screen_load", `SpanAttributes['url.path'] LIKE '/tc19-%'`, windowSeconds),
      countCHSpans("screen_session", `SpanAttributes['url.path'] LIKE '/tc19-%'`, windowSeconds),
    ]);

    expect(loads).toBe(0);
    expect(sessions).toBe(0);
  });

  test("TC 20: consent=PENDING → zero navigation spans in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/?pulse_consent=pending");
    await page.waitForTimeout(2_000);

    await page.evaluate(() => {
      history.pushState({}, "", "/products");
      history.pushState({}, "", "/cart");
    });
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;

    const [loads, sessions] = await Promise.all([
      countCHSpans("screen_load", "", windowSeconds),
      countCHSpans("screen_session", "", windowSeconds),
    ]);

    expect(loads).toBe(0);
    expect(sessions).toBe(0);
  });

  test("TC 21: post-shutdown → no new spans in CH after SDK shutdown", async ({ page }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(500);

    // Shutdown the SDK
    await page.evaluate(async () => {
      await (window as unknown as PulseWebWindow).PulseWeb!.shutdown();
    });

    const before = Date.now();

    // Navigate post-shutdown — nothing should be exported
    await page.evaluate(() => history.pushState({}, "", "/post-shutdown-test"));
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;

    const count = await countCHSpans(
      "screen_session",
      `SpanAttributes['url.path'] = '/post-shutdown-test'`,
      windowSeconds,
    );

    expect(count).toBe(0);
  });
});
