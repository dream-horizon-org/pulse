import { test, expect, findAllSpans, getAttr } from "./fixture";
import type { Page, Route } from "@playwright/test";

const INTERACTION_CONFIG = [
  {
    id: "checkout_flow",
    name: "Checkout Flow",
    events: [
      { name: "checkout_step_1", required: true },
      { name: "checkout_step_2", required: true },
      { name: "checkout_step_3", required: true },
    ],
    thresholdInMs: 5000,
    uptimeLowerLimitInMs: 1000,
    uptimeMidLimitInMs: 3000,
    uptimeUpperLimitInMs: 6000,
    globalBlacklistedEvents: [],
  },
];

async function seedInteractionConfig(
  page: Page,
  payload: unknown,
): Promise<void> {
  await page.route("**/v1/interaction-configs/", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(payload),
    });
  });
}

test.describe("@M2 interactions e2e", () => {
  test("checkout flow emits interaction span with pulse.interaction.* attrs", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, INTERACTION_CONFIG);
    await page.goto("/checkout");

    await page.getByTestId("checkout-step-1-next").click();
    await page.getByTestId("checkout-step-2-next").click();
    await page.getByTestId("checkout-step-3-confirm").click();

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.id")).toBeTruthy();
    expect(getAttr(span.attributes, "pulse.interaction.name")).toBeTruthy();
    expect(
      getAttr(span.attributes, "pulse.interaction.config.id"),
    ).toBeTruthy();
    expect(
      getAttr(span.attributes, "pulse.interaction.complete_time"),
    ).toBeTruthy();
    expect(
      getAttr(span.attributes, "pulse.interaction.apdex_score"),
    ).toBeDefined();
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toMatch(
      /Excellent|Good|Average|Poor/,
    );
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("timeout mid-sequence emits interaction error span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, INTERACTION_CONFIG);
    await page.goto("/checkout");
    await page.getByTestId("checkout-step-1-next").click();

    // Config in demo uses 5s threshold for checkout flow.
    await page.waitForTimeout(6500);

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  test("complete_time nanos is consistent with span start/end nanos", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, INTERACTION_CONFIG);
    await page.goto("/checkout");
    await page.getByTestId("checkout-step-1-next").click();
    await page.getByTestId("checkout-step-2-next").click();
    await page.getByTestId("checkout-step-3-confirm").click();

    const span = await otlp.waitForSpan("interaction", 15_000);
    const completeTimeNs = Number(
      getAttr(span.attributes, "pulse.interaction.complete_time"),
    );
    const startNs = Number(span.startTimeUnixNano);
    const endNs = Number(span.endTimeUnixNano);

    expect(Number.isFinite(completeTimeNs)).toBe(true);
    expect(completeTimeNs).toBeGreaterThan(0);
    expect(endNs).toBeGreaterThan(startNs);
    expect(endNs - startNs).toBeGreaterThanOrEqual(completeTimeNs);
  });

  test("out-of-order event emits interaction error span", async ({
    page,
    otlp,
  }) => {
    const strictTwoStepConfig = [
      {
        ...INTERACTION_CONFIG[0],
        events: [
          { name: "checkout_step_1", required: true },
          { name: "checkout_step_2", required: true },
        ],
      },
    ];
    await seedInteractionConfig(page, strictTwoStepConfig);
    await page.goto("/checkout");
    await page.getByTestId("checkout-step-1-next").click();
    await page.evaluate(() => {
      const w = window as unknown as {
        PulseWeb?: { trackEvent?: (name: string) => void };
      };
      w.PulseWeb?.trackEvent?.("checkout_step_3");
    });

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(["sequence_violation", "timeout"]).toContain(
      getAttr(span.attributes, "pulse.interaction.error.type"),
    );
  });

  test("two independent interactions each emit a span", async ({
    page,
    otlp,
  }) => {
    const singleStepConfig = [
      {
        ...INTERACTION_CONFIG[0],
        events: [{ name: "checkout_step_1", required: true }],
      },
    ];
    await seedInteractionConfig(page, singleStepConfig);
    await page.goto("/checkout");
    await page.getByTestId("checkout-step-1-next").click();
    await otlp.waitForSpan("interaction", 15_000);

    await page.evaluate(() => {
      const w = window as unknown as {
        PulseWeb?: { trackEvent?: (name: string) => void };
      };
      w.PulseWeb?.trackEvent?.("checkout_step_1");
    });
    for (let i = 0; i < 30; i += 1) {
      if (findAllSpans(otlp.captured, "interaction").length >= 2) break;
      await page.waitForTimeout(200);
    }

    const spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(2);
    expect(
      spans.every(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  test("global blacklist cancels in-flight sequence without emitting error span", async ({
    page,
    otlp,
  }) => {
    const withBlacklist = [
      {
        ...INTERACTION_CONFIG[0],
        globalBlacklistedEvents: ["ad_impression"],
      },
    ];
    await seedInteractionConfig(page, withBlacklist);

    await page.goto("/checkout");
    await page.getByTestId("checkout-step-1-next").click();
    await page.evaluate(() => {
      const w = window as unknown as {
        PulseWeb?: { trackEvent?: (name: string) => void };
      };
      w.PulseWeb?.trackEvent?.("ad_impression");
    });

    await page.waitForTimeout(6500);

    const spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(0);
  });

  test("interaction config fetch unavailable -> no interaction span, sdk still running", async ({
    page,
    otlp,
  }) => {
    await page.route("**/v1/interaction-configs/", async (route) => {
      await route.fulfill({
        status: 503,
        contentType: "application/json",
        body: "{}",
      });
    });
    await page.goto("/checkout");
    await otlp.waitForLog("session.start", 10_000);
    otlp.reset();

    await page.getByTestId("checkout-step-1-next").click();
    await page.getByTestId("checkout-step-2-next").click();
    await page.getByTestId("checkout-step-3-confirm").click();
    // Negative assertion: wait a guaranteed interval then confirm nothing arrived.
    await page.waitForTimeout(2000);
    expect(findAllSpans(otlp.captured, "interaction").length).toBe(0);
  });
});
