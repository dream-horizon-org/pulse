/**
 * M4 E2E Tests — Navigation Instrumentation (TC 1–21)
 *
 * Covers NavigationInstrumentation: screen_load, screen_interactive, screen_session.
 *
 *   TC 1–4   : page-load spans (navigate / reload / back_forward)
 *   TC 5–6   : SPA session tracking + previous_screen.name chain
 *   TC 7     : routePatterns config → custom screen.name
 *   TC 8–9   : heuristic strip (numeric IDs, UUIDs)
 *   TC 10–11 : setScreenName override + auto-clear on next nav
 *   TC 12    : pagehide → final screen_session
 *   TC 13–14 : screen.name + url.path on ALL three span types
 *   TC 15–18 : negative guards (sub-100ms, replaceState, same-route, hash)
 *   TC 19–21 : consent DENIED / pre-init nav / post-shutdown nav
 *
 * Run:  yarn e2e --grep "@M4"          (from examples/ecommerce-demo/)
 *       yarn e2e --grep "@M4" --headed  (headed for debugging)
 */

import {
  test,
  expect,
  getAttr,
  findAllSpansByName,
  type OtlpSpan,
  type CapturedRequest,
} from "./fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

/** 3.5× the 200ms test batch window — enough to capture one export cycle. */
const FLUSH = 800;

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
        page.evaluate(() => {
          return (window as unknown as PulseWebWindow).PulseWeb?.isInitialized?.() ?? false;
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

function screenLoads(captured: CapturedRequest[]): OtlpSpan[] {
  return findAllSpansByName(captured, "screen_load");
}

function screenInteractives(captured: CapturedRequest[]): OtlpSpan[] {
  return findAllSpansByName(captured, "screen_interactive");
}

function screenSessions(captured: CapturedRequest[]): OtlpSpan[] {
  return findAllSpansByName(captured, "screen_session");
}

// ─── TC 1–4: Initial Page Load ────────────────────────────────────────────────

test.describe("@M4 page-load spans", () => {
  test("TC 1: screen_load on cold navigate — type=navigate, start=cold, perf attrs set", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    const span = await otlp.waitForSpanByName("screen_load");

    expect(getAttr(span.attributes, "pulse.type")).toBe("screen_load");
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    expect(getAttr(span.attributes, "url.path")).toBe("/products");
    expect(getAttr(span.attributes, "navigation.type")).toBe("navigate");
    expect(getAttr(span.attributes, "start.type")).toBe("cold");
    expect(Number(getAttr(span.attributes, "load.duration_ms"))).toBeGreaterThan(0);
    expect(Number(getAttr(span.attributes, "ttfb_ms"))).toBeGreaterThanOrEqual(0);
  });

  test("TC 2: screen_interactive on page load — tti > 0", async ({ page, otlp }) => {
    await page.goto("/products");
    const span = await otlp.waitForSpanByName("screen_interactive");

    expect(getAttr(span.attributes, "pulse.type")).toBe("screen_interactive");
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    expect(getAttr(span.attributes, "url.path")).toBe("/products");
    expect(Number(getAttr(span.attributes, "tti"))).toBeGreaterThan(0);
  });

  test("TC 3: start.type=reload on hard page reload", async ({ page, otlp }) => {
    await page.goto("/products");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();

    await page.reload();
    const span = await otlp.waitForSpanByName("screen_load");

    expect(getAttr(span.attributes, "navigation.type")).toBe("reload");
    expect(getAttr(span.attributes, "start.type")).toBe("reload");
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
  });

  test("TC 4: start.type=back_forward on browser back", async ({ page, otlp }) => {
    // Navigate / → /products so there is history to go back to
    await page.goto("/");
    await waitForSdkInit(page);
    await page.goto("/products");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();

    // Browser back → / (back_forward navigation)
    await page.goBack();
    const span = await otlp.waitForSpanByName("screen_load");

    expect(getAttr(span.attributes, "navigation.type")).toBe("back_forward");
    expect(getAttr(span.attributes, "start.type")).toBe("back_forward");
  });
});

// ─── TC 5–6: SPA Session Tracking ────────────────────────────────────────────

test.describe("@M4 SPA screen_session", () => {
  test("TC 5: first SPA nav → previous_screen.name is empty string", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    // Wait for session.start (same pattern as m2.spec.ts — reliable SDK-ready signal)
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(200); // ensure routeStartTime is > 100ms ago
    otlp.reset();

    // Trigger SPA nav via pushState (avoids Playwright link-click navigation detection)
    await page.evaluate(() => history.pushState({}, "", "/products"));
    const span = await otlp.waitForSpanByName("screen_session");

    // Session for the / screen that just ended
    expect(getAttr(span.attributes, "screen.name")).toBe("/");
    expect(getAttr(span.attributes, "previous_screen.name")).toBe("");
    expect(Number(getAttr(span.attributes, "session.duration"))).toBeGreaterThan(0);
  });

  test("TC 6: second SPA nav → previous_screen.name equals the prior screen", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(200);

    // / → /products (produces session for /)
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await otlp.waitForSpanByName("screen_session");
    await page.waitForTimeout(200); // stay on /products > 100ms
    otlp.reset();

    // /products → /cart (produces session for /products)
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    const span = await otlp.waitForSpanByName("screen_session");

    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    expect(getAttr(span.attributes, "previous_screen.name")).toBe("/");
  });
});

// ─── TC 7: Route Patterns ─────────────────────────────────────────────────────

test.describe("@M4 routePatterns custom screen.name", () => {
  test("TC 7: routePatterns config → matched route gets custom screen.name", async ({
    page,
    otlp,
  }) => {
    // Injected before React mounts — picked up by App.tsx useMemo
    await page.addInitScript(() => {
      (window as unknown as Record<string, unknown>)["__pulseE2eRoutePatterns"] = [
        { pattern: "/products/:id", name: "ProductDetail" },
      ];
    });

    await page.goto("/products/123");
    const span = await otlp.waitForSpanByName("screen_load");

    expect(getAttr(span.attributes, "screen.name")).toBe("ProductDetail");
    expect(getAttr(span.attributes, "url.path")).toBe("/products/123");
  });
});

// ─── TC 8–9: Heuristic Screen Name ───────────────────────────────────────────

test.describe("@M4 screen.name heuristics", () => {
  test("TC 8: numeric ID segment stripped → screen.name=/products", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    otlp.reset();

    // Navigate to /products/123 via pushState (no route pattern)
    await page.evaluate(() => history.pushState({}, "", "/products/123"));
    await page.waitForTimeout(300);

    // Navigate away to flush the session
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(FLUSH);

    const sess = screenSessions(otlp.captured).find(
      (s) => String(getAttr(s.attributes, "url.path") ?? "").startsWith("/products/"),
    );
    expect(sess).toBeDefined();
    expect(getAttr(sess!.attributes, "screen.name")).toBe("/products");
  });

  test("TC 9: UUID segment stripped → screen.name=/orders", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    otlp.reset();

    const uuid = "550e8400-e29b-41d4-a716-446655440000";
    await page.evaluate(
      (id) => history.pushState({}, "", `/orders/${id}`),
      uuid,
    );
    await page.waitForTimeout(300);
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(FLUSH);

    const sess = screenSessions(otlp.captured).find((s) =>
      String(getAttr(s.attributes, "url.path") ?? "").startsWith("/orders/"),
    );
    expect(sess).toBeDefined();
    expect(getAttr(sess!.attributes, "screen.name")).toBe("/orders");
  });
});

// ─── TC 10–11: setScreenName ──────────────────────────────────────────────────

test.describe("@M4 setScreenName", () => {
  test("TC 10: setScreenName override is used on screen_session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products/123");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(200); // ensure > 100ms session duration
    otlp.reset();

    // Override screen name while on /products/123
    await page.evaluate(() => {
      (window as unknown as PulseWebWindow).PulseWeb!.setScreenName("FeaturedProduct");
    });

    // Navigate away → session for /products/123 should carry override name
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    const span = await otlp.waitForSpanByName("screen_session");

    expect(getAttr(span.attributes, "screen.name")).toBe("FeaturedProduct");
  });

  test("TC 11: setScreenName is cleared after next navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);

    // Set override on /products
    await page.evaluate(() => {
      (window as unknown as PulseWebWindow).PulseWeb!.setScreenName("Override");
    });

    // Navigate to /cart — override should be cleared for /cart spans
    await page.click('a[href="/cart"]');
    await page.waitForTimeout(FLUSH);
    otlp.reset();

    // Navigate from /cart → /checkout to get the /cart screen_session
    await page.click('a[href="/checkout"]');
    await page.waitForTimeout(FLUSH);

    const cartSession = screenSessions(otlp.captured).find(
      (s) => getAttr(s.attributes, "url.path") === "/cart",
    );
    expect(cartSession).toBeDefined();
    // Override must NOT bleed through to /cart
    expect(getAttr(cartSession!.attributes, "screen.name")).toBe("/cart");
  });
});

// ─── TC 12: pagehide → final screen_session ───────────────────────────────────

test.describe("@M4 pagehide", () => {
  test("TC 12: pagehide dispatched → screen_session emitted for current route", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(200); // stay on page > 100ms (sub-100ms guard)
    otlp.reset();

    // Dispatch pagehide (simulates tab close / navigation away from BFCache-ineligible page)
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    const span = await otlp.waitForSpanByName("screen_session");

    expect(getAttr(span.attributes, "screen.name")).toBe("/");
    expect(Number(getAttr(span.attributes, "session.duration"))).toBeGreaterThanOrEqual(100);
  });
});

// ─── TC 13–14: Global attrs on all three span types ──────────────────────────

test.describe("@M4 screen.name + url.path on all span types", () => {
  test("TC 13–14: screen.name and url.path present on screen_load, screen_interactive, screen_session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(200); // ensure > 100ms before nav

    // Trigger screen_session by navigating away — wait for it explicitly
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await otlp.waitForSpanByName("screen_session"); // wait for flush

    const load = screenLoads(otlp.captured).find(
      (s) => getAttr(s.attributes, "url.path") === "/products",
    );
    const interactive = screenInteractives(otlp.captured).find(
      (s) => getAttr(s.attributes, "url.path") === "/products",
    );
    const session = screenSessions(otlp.captured).find(
      (s) => getAttr(s.attributes, "url.path") === "/products",
    );

    expect(load).toBeDefined();
    expect(interactive).toBeDefined();
    expect(session).toBeDefined();

    // TC 13: screen.name
    expect(getAttr(load!.attributes, "screen.name")).toBe("/products");
    expect(getAttr(interactive!.attributes, "screen.name")).toBe("/products");
    expect(getAttr(session!.attributes, "screen.name")).toBe("/products");

    // TC 14: url.path
    expect(getAttr(load!.attributes, "url.path")).toBe("/products");
    expect(getAttr(interactive!.attributes, "url.path")).toBe("/products");
    expect(getAttr(session!.attributes, "url.path")).toBe("/products");
  });
});

// ─── TC 15–18: Negative / Guard Tests ────────────────────────────────────────

test.describe("@M4 negative: session guards", () => {
  test("TC 15: sub-100ms rapid pushState → session for fast route suppressed", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    otlp.reset();

    // Two synchronous pushStates — /a duration will be < 1ms → sub-100ms guard fires
    await page.evaluate(() => {
      history.pushState({}, "", "/a");
      history.pushState({}, "", "/b");
    });
    await page.waitForTimeout(300);

    // Navigate away to flush /b session
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(FLUSH);

    const aSessions = screenSessions(otlp.captured).filter(
      (s) => getAttr(s.attributes, "url.path") === "/a",
    );
    expect(aSessions.length).toBe(0); // /a suppressed by < 100ms guard
  });

  test("TC 16: replaceState does NOT create a new session — single session covers full time", async ({
    page,
    otlp,
  }) => {
    await page.goto("/checkout");
    await waitForSdkInit(page);
    await page.waitForTimeout(300);
    otlp.reset();

    // URL cleanup (e.g. removing auth token from query string) — same pathname
    await page.evaluate(() =>
      history.replaceState({}, "", "/checkout?step=2"),
    );
    await page.waitForTimeout(100);

    // Navigate away
    await page.click('a[href="/cart"]');
    await page.waitForTimeout(FLUSH);

    const checkoutSessions = screenSessions(otlp.captured).filter((s) =>
      String(getAttr(s.attributes, "url.path") ?? "").startsWith("/checkout"),
    );
    // Exactly one session for /checkout — replaceState must not split it
    expect(checkoutSessions.length).toBe(1);
  });

  test("TC 17: same-route pushState does NOT split session — one session, combined duration", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(200);
    otlp.reset();

    // pushState with same pathname — should be a no-op for the session
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(200);

    await page.click('a[href="/cart"]');
    await page.waitForTimeout(FLUSH);

    const productsSessions = screenSessions(otlp.captured).filter(
      (s) => getAttr(s.attributes, "url.path") === "/products",
    );
    expect(productsSessions.length).toBe(1);
    // Duration should span both waits (≥ 350ms)
    expect(
      Number(getAttr(productsSessions[0]!.attributes, "session.duration")),
    ).toBeGreaterThan(350);
  });

  test("TC 18: hash-only pushState does NOT split session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(200);
    otlp.reset();

    // Hash fragment — pathname is still /products
    await page.evaluate(() =>
      history.pushState({}, "", "/products#section"),
    );
    await page.waitForTimeout(200);

    await page.click('a[href="/cart"]');
    await page.waitForTimeout(FLUSH);

    const productsSessions = screenSessions(otlp.captured).filter(
      (s) => getAttr(s.attributes, "url.path") === "/products",
    );
    expect(productsSessions.length).toBe(1);
  });
});

// ─── TC 19–21: Consent / Lifecycle ───────────────────────────────────────────

test.describe("@M4 negative: consent / lifecycle", () => {
  test("TC 19: consent=DENIED → zero navigation spans emitted", async ({
    page,
    otlp,
  }) => {
    // SDK init returns early when consent is DENIED — no TracerProvider created
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(FLUSH);

    // SPA navigations that would normally emit screen_session
    await page.evaluate(() => {
      history.pushState({}, "", "/products");
      history.pushState({}, "", "/cart");
    });
    await page.waitForTimeout(FLUSH);

    expect(screenLoads(otlp.captured).length).toBe(0);
    expect(screenInteractives(otlp.captured).length).toBe(0);
    expect(screenSessions(otlp.captured).length).toBe(0);
  });

  test("TC 20: navigation before SDK init → no spans", async ({
    page,
    otlp,
  }) => {
    // Load with consent=denied — SDK never initializes, no NavigationInstrumentation installed.
    // Then do pushState programmatically. Verifies that without SDK, pushState is a no-op.
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(FLUSH);

    // Pushes that would emit spans if SDK were running
    await page.evaluate(() => {
      history.pushState({}, "", "/products");
      history.pushState({}, "", "/cart");
    });
    await page.waitForTimeout(FLUSH);

    expect(screenLoads(otlp.captured).length).toBe(0);
    expect(screenSessions(otlp.captured).length).toBe(0);
  });

  test("TC 21: after PulseWeb.shutdown() → pushState emits no new spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(200);

    // Shutdown uninstalls NavigationInstrumentation and restores history.pushState
    await page.evaluate(async () => {
      await (window as unknown as PulseWebWindow).PulseWeb!.shutdown();
    });
    otlp.reset();

    // Navigation after shutdown — should be transparent to the SDK
    await page.evaluate(() => history.pushState({}, "", "/test"));
    await page.waitForTimeout(FLUSH);

    expect(screenSessions(otlp.captured).length).toBe(0);
    expect(screenLoads(otlp.captured).length).toBe(0);
  });
});
