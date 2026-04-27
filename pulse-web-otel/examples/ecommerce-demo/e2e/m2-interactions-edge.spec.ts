import { expect, findAllSpans, getAttr, test } from "./fixture";
import {
  emitEvent,
  gotoAndWaitInteractionInit,
  makeConfig,
  seedInteractionConfig,
  setUserId,
  waitForInteractionCount,
} from "./interaction-test-helpers";

async function expectNoInteractionSpans(
  captured: unknown[],
  pageWait: (ms: number) => Promise<void>,
  waitMs = 800,
): Promise<void> {
  await pageWait(waitMs);
  expect(findAllSpans(captured as never[], "interaction").length).toBe(0);
}

test.describe("@M2 interactions edge cases", () => {
  const operatorCases = [
    { operator: "EQUALS", expected: "gold", pass: "gold", fail: "silver" },
    { operator: "NOT_EQUALS", expected: "gold", pass: "silver", fail: "gold" },
    {
      operator: "CONTAINS",
      expected: "pro",
      pass: "pro_plus",
      fail: "starter",
    },
    {
      operator: "NOT_CONTAINS",
      expected: "internal",
      pass: "external",
      fail: "internal-testing",
    },
    {
      operator: "STARTS_WITH",
      expected: "plan_",
      pass: "plan_enterprise",
      fail: "enterprise_plan",
    },
    {
      operator: "ENDS_WITH",
      expected: "_us",
      pass: "region_us",
      fail: "us_region",
    },
  ] as const;

  for (const op of operatorCases) {
    test(`property operator ${op.operator} positive match emits span`, async ({
      page,
      otlp,
    }) => {
      await seedInteractionConfig(page, [
        makeConfig({
          id: `props_${op.operator.toLowerCase()}_ok`,
          name: `Props ${op.operator} OK`,
          events: [
            {
              name: "props_event",
              required: true,
              props: [{ key: "plan", value: op.expected, operator: op.operator }],
            },
          ],
        }),
      ]);
      await gotoAndWaitInteractionInit(page);
      await emitEvent(page, "props_event", { plan: op.pass });

      const span = await otlp.waitForSpan("interaction", 10_000);
      expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
      expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe(
        `props_${op.operator.toLowerCase()}_ok`,
      );
    });

    test(`property operator ${op.operator} negative match blocks span, positive still works`, async ({
      page,
      otlp,
    }) => {
      await seedInteractionConfig(page, [
        makeConfig({
          id: `props_${op.operator.toLowerCase()}_ko`,
          name: `Props ${op.operator} KO`,
          events: [
            {
              name: "props_event",
              required: true,
              props: [{ key: "plan", value: op.expected, operator: op.operator }],
            },
          ],
        }),
      ]);
      await gotoAndWaitInteractionInit(page);
      await emitEvent(page, "props_event", { plan: op.fail });

      await expectNoInteractionSpans(otlp.captured, page.waitForTimeout.bind(page));
      // Prove the flow is active and matcher can still emit a terminal on valid input.
      await emitEvent(page, "props_event", { plan: op.pass });
      const span = await otlp.waitForSpan("interaction", 10_000);
      expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    });
  }

  test("exploratory: required=false is not skippable in current matcher", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "optional_not_skippable",
        name: "Optional Not Skippable",
        events: [
          { name: "start", required: true },
          { name: "middle_optional", required: false },
          { name: "end", required: true },
        ],
        thresholdInMs: 800,
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "start");
    await emitEvent(page, "end");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  test("required=false present in order allows success", async ({ page, otlp }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "optional_present_success",
        name: "Optional Present Success",
        events: [
          { name: "start", required: true },
          { name: "middle_optional", required: false },
          { name: "end", required: true },
        ],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "start");
    await emitEvent(page, "middle_optional");
    await emitEvent(page, "end");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("overlapping configs on same stream each emit terminal span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "overlap_a",
        name: "Overlap A",
        events: [
          { name: "start", required: true },
          { name: "finish_a", required: true },
        ],
      }),
      makeConfig({
        id: "overlap_b",
        name: "Overlap B",
        events: [
          { name: "start", required: true },
          { name: "finish_b", required: true },
        ],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "start");
    await emitEvent(page, "finish_a");
    await emitEvent(page, "finish_b");
    await waitForInteractionCount(page, otlp, 2, 10_000);

    const spans = findAllSpans(otlp.captured, "interaction");
    const configIds = spans.map((s) =>
      String(getAttr(s.attributes, "pulse.interaction.config.id")),
    );
    expect(configIds).toContain("overlap_a");
    expect(configIds).toContain("overlap_b");
    expect(
      spans.every(
        (span) => getAttr(span.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  test("out-of-order event timestamp leads to timeout error", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "timestamp_order",
        name: "Timestamp Order",
        events: [
          { name: "ts_a", required: true },
          { name: "ts_b", required: true },
        ],
        thresholdInMs: 700,
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    const now = Date.now();
    await emitEvent(page, "ts_a", undefined, now + 200);
    await emitEvent(page, "ts_b", undefined, now - 200);

    const span = await otlp.waitForSpan("interaction", 12_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  test("restart after sequence violation emits error then new success", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "restart_after_violation",
        name: "Restart After Violation",
        events: [
          { name: "first", required: true },
          { name: "second", required: true },
        ],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "first");
    await emitEvent(page, "first");
    await emitEvent(page, "second");
    await waitForInteractionCount(page, otlp, 2, 12_000);

    const spans = findAllSpans(otlp.captured, "interaction").filter(
      (s) =>
        getAttr(s.attributes, "pulse.interaction.config.id") ===
        "restart_after_violation",
    );
    expect(spans.length).toBe(2);
    const errored = spans.find(
      (s) => getAttr(s.attributes, "pulse.interaction.is_error") === true,
    );
    const success = spans.find(
      (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
    );
    expect(errored).toBeDefined();
    expect(success).toBeDefined();
    expect(getAttr(errored?.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
    // Restart path should create a new in-flight interaction identity.
    expect(getAttr(errored?.attributes, "pulse.interaction.id")).not.toBe(
      getAttr(success?.attributes, "pulse.interaction.id"),
    );
  });

  test("multiple global blacklist hits cancel flow and later flow can still succeed", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "multi_blacklist",
        name: "Multi Blacklist",
        events: [
          { name: "step_1", required: true },
          { name: "step_2", required: true },
        ],
        globalBlacklistedEvents: ["blacklist_event"],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);

    await emitEvent(page, "step_1");
    await emitEvent(page, "blacklist_event");
    await emitEvent(page, "step_1");
    await emitEvent(page, "blacklist_event");
    await page.waitForTimeout(600);
    expect(findAllSpans(otlp.captured, "interaction").length).toBe(0);

    await emitEvent(page, "step_1");
    await emitEvent(page, "step_2");
    const span = await otlp.waitForSpan("interaction", 8_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("mixed valid + invalid config payload gets rejected as array", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "valid_flow",
        name: "Valid Flow",
        events: [
          { name: "valid_a", required: true },
          { name: "valid_b", required: true },
        ],
      }),
      { id: "invalid_missing_fields", events: [] },
    ]);
    await gotoAndWaitInteractionInit(page);
    await otlp.waitForLog("session.start", 10_000);
    otlp.reset();
    await emitEvent(page, "valid_a");
    await emitEvent(page, "valid_b");

    await expectNoInteractionSpans(otlp.captured, page.waitForTimeout.bind(page));
    // Guard against false positives: SDK must still emit normal custom events.
    await otlp.waitForLogByBody("valid_b", 10_000);
  });

  test("user id updated mid-interaction stamps final interaction span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "user_mid_flow",
        name: "User Mid Flow",
        events: [
          { name: "user_a", required: true },
          { name: "user_b", required: true },
        ],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await setUserId(page, "user-old");
    await emitEvent(page, "user_a");
    await setUserId(page, "user-new");
    await emitEvent(page, "user_b");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "user.id")).toBe("user-new");
  });

  test("apdex exact boundary lower=>Excellent mid=>Good upper=>Average", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "apdex_boundaries",
        name: "Apdex Boundaries",
        events: [
          { name: "apdex_a", required: true },
          { name: "apdex_b", required: true },
        ],
        thresholdInMs: 1000,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 240,
        uptimeUpperLimitInMs: 360,
      }),
    ]);
    await gotoAndWaitInteractionInit(page);

    const t0 = Date.now();
    await emitEvent(page, "apdex_a", undefined, t0);
    await emitEvent(page, "apdex_b", undefined, t0 + 120);
    await emitEvent(page, "apdex_a", undefined, t0 + 1_000);
    await emitEvent(page, "apdex_b", undefined, t0 + 1_240);
    await emitEvent(page, "apdex_a", undefined, t0 + 2_000);
    await emitEvent(page, "apdex_b", undefined, t0 + 2_360);
    await waitForInteractionCount(page, otlp, 3, 10_000);

    const spans = findAllSpans(otlp.captured, "interaction").filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "apdex_boundaries",
    );
    expect(spans.length).toBe(3);
    const categories = new Set(
      spans.map((span) =>
        String(getAttr(span.attributes, "pulse.interaction.user_category")),
      ),
    );
    expect(categories).toEqual(new Set(["Excellent", "Good", "Average"]));
    // Boundary determinism: no degraded category for exact limits.
    expect(categories.has("Poor")).toBe(false);
  });
});
