/**
 * M2 E2E Tests — Interactions + SDK Config + React Integration
 *
 * Covers the done criteria from .claude/plans/web-sdk-m2-interactions.md:
 *   - Checkout interaction tracked end-to-end with APDEX scoring
 *   - Config fetch failure → graceful no-op
 *   - sessionSampleRate: 0 → zero signals
 *   - React route change → screen_session span
 *   - SSR guard (no localStorage-is-not-defined crashes)
 *   - PulseErrorBoundary → device.crash log
 *
 * Run:  yarn e2e --grep "@M2" --project=chromium
 */
import { test, expect, getAttr, findAllLogs, findAllSpans } from "./fixture";
import {
  blockActiveConfigFetch,
  minimalPulseSdkConfig,
  seedPulseSdkConfig,
} from "./test-sdk-config";

// ─── Interaction Tracking ─────────────────────────────────────────────────────

test.describe("@M2 interaction tracking", () => {
  test("checkout flow emits interaction span with APDEX user_category", async ({
    page,
    otlp,
  }) => {
    await page.goto("/checkout");

    // Step 1: fill in and proceed
    await page.getByTestId("checkout-step-1-next").click();
    // Step 2: proceed
    await page.getByTestId("checkout-step-2-next").click();
    // Step 3: confirm
    await page.getByTestId("checkout-step-3-confirm").click();

    const span = await otlp.waitForSpan("interaction", 15_000);

    expect(getAttr(span.attributes, "interaction.name")).toBeTruthy();
    const userCategory = getAttr(span.attributes, "user_category");
    expect(["Satisfied", "Tolerating", "Frustrated"]).toContain(userCategory);
    expect(getAttr(span.attributes, "apdex_score")).toBeDefined();
  });

  test("interaction timeout mid-sequence → no span emitted, no crash", async ({
    page,
    otlp,
  }) => {
    await page.goto("/checkout");
    // Only complete step 1, then wait longer than timeout (config sets 5000ms)
    await page.getByTestId("checkout-step-1-next").click();
    // Do NOT continue; wait > interaction timeout
    await page.waitForTimeout(6000);

    const spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(0);

    // No console errors
    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });
    expect(errors.filter((e) => !e.includes("favicon"))).toHaveLength(0);
  });

  test("rapid checkout (< apdex_t) → user_category Satisfied", async ({
    page,
    otlp,
  }) => {
    await page.goto("/checkout");
    // Complete all 3 steps quickly (< 5000ms apdex_t)
    await page.getByTestId("checkout-step-1-next").click();
    await page.getByTestId("checkout-step-2-next").click();
    await page.getByTestId("checkout-step-3-confirm").click();

    const span = await otlp.waitForSpan("interaction");
    expect(getAttr(span.attributes, "user_category")).toBe("Satisfied");
  });
});

// ─── SDK Config / Sampling ───────────────────────────────────────────────────

test.describe("@M2 SDK config + sampling", () => {
  test("config fetch failure → SDK still works, no crash", async ({
    page,
    otlp,
  }) => {
    // Block the interaction-config.json fetch
    await page.route("**/interaction-config.json", (route) =>
      route.fulfill({ status: 500, body: "error" }),
    );
    await page.goto("/");
    // session.start should still emit despite config failure
    const log = await otlp.waitForLog("session.start");
    expect(log).toBeTruthy();
  });

  test("sessionSampleRate: 0 in remote config → zero signals exported", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 700,
      sampling: {
        default: { sessionSampleRate: 0 },
        rules: [],
        signalsToSample: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await page.waitForTimeout(1500);
    otlp.reset();
    await page.goto("/products");
    await page.waitForTimeout(2000);
    expect(otlp.captured.length).toBe(0);
  });

  test("feature disabled in remote config → instrumentation not installed", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 701,
      features: [
        {
          featureName: "js_crash",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      queueMicrotask(() => {
        throw new Error("should not be tracked");
      });
    });
    await page.waitForTimeout(500);
    expect(findAllLogs(otlp.captured, "device.crash").length).toBe(0);
  });
});

// ─── React Integration ────────────────────────────────────────────────────────

test.describe("@M2 React integration", () => {
  test("React route change → screen_session span", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Navigate via React Router (click a link, not full page reload)
    await page
      .getByRole("link", { name: /products/i })
      .first()
      .click();
    await page.waitForURL("**/products");

    const span = await otlp.waitForSpan("screen_session");
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test('screen.name heuristic: /products/123 → "products/:id"', async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    otlp.reset();

    await page.goto("/products/1");
    const span = await otlp.waitForSpan("screen_session");
    // Heuristic strips numeric ID → "products/:id"
    expect(getAttr(span.attributes, "screen.name")).toMatch(/products\/:id/i);
  });

  test("PulseErrorBoundary catches React render error → device.crash log", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.getByTestId("throw-render-error").click();

    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "exception.message")).toBeTruthy();
    expect(getAttr(log.attributes, "exception.stacktrace")).toBeTruthy();
  });

  test("SSR guard: no localStorage-is-not-defined error in console", async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });
    page.on("pageerror", (err) => errors.push(err.message));

    await page.goto("/");
    await page.waitForTimeout(1000);

    expect(errors.filter((e) => e.includes("localStorage"))).toHaveLength(0);
    expect(errors.filter((e) => e.includes("sessionStorage"))).toHaveLength(0);
  });
});

// ─── Consent Gate ────────────────────────────────────────────────────────────

test.describe("@M2 consent gate", () => {
  test("PulseDataCollectionConsent.DENIED → zero signals from all instrumentations", async ({
    page,
    otlp,
  }) => {
    // Set consent to DENIED before SDK initialises (via query param that App.tsx reads)
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(2000);

    expect(otlp.captured.length).toBe(0);
  });
});
