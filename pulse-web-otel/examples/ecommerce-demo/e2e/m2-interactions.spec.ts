import { test, expect, getAttr } from "./fixture";

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

test.describe("@M2 interactions e2e", () => {
  test("checkout flow emits interaction span with pulse.interaction.* attrs", async ({
    page,
    otlp,
  }) => {
    await page.route("**/v1/interaction-configs/", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(INTERACTION_CONFIG),
      });
    });
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
    await page.route("**/v1/interaction-configs/", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(INTERACTION_CONFIG),
      });
    });
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
});
