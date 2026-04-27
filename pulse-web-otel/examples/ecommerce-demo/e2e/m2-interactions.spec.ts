import {
  test,
  expect,
  findAllSpans,
  getAttr,
  getResourceAttr,
} from "./fixture";
import {
  emitEvent,
  gotoAndWaitInteractionInit,
  makeConfig,
  seedInteractionConfig,
  waitForInteractionCount,
} from "./interaction-test-helpers";

const MANUAL_FLOW = makeConfig({
  id: "manual_checkout_flow",
  name: "Manual Checkout Flow",
  events: [
    { name: "checkout_step_1", required: true },
    { name: "checkout_step_2", required: true },
    { name: "checkout_step_3", required: true },
  ],
  thresholdInMs: 700,
  uptimeLowerLimitInMs: 120,
  uptimeMidLimitInMs: 260,
  uptimeUpperLimitInMs: 420,
});

test.describe("@M2 interactions e2e", () => {
  test("single-event interaction emits success span", async ({ page, otlp }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "single_event",
          name: "Single Event",
          events: [{ name: "single_event", required: true }],
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "single_event");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe(
      "single_event",
    );
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("two-event interaction emits success span with contract attrs", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "two_step",
          name: "Two Step",
          events: [
            { name: "step_one", required: true },
            { name: "step_two", required: true },
          ],
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "step_one");
    await page.waitForTimeout(80);
    await emitEvent(page, "step_two");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.type")).toBe("interaction");
    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
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

  test("multi-event interaction emits success span", async ({ page, otlp }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await page.waitForTimeout(40);
    await emitEvent(page, "checkout_step_2");
    await page.waitForTimeout(40);
    await emitEvent(page, "checkout_step_3");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe(
      "manual_checkout_flow",
    );
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("ignored event does not break an in-flight interaction", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "totally_irrelevant_event");
    await page.waitForTimeout(70);
    await emitEvent(page, "checkout_step_2");
    await page.waitForTimeout(70);
    await emitEvent(page, "checkout_step_3");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("global blacklist cancels in-flight sequence without emitting span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        ...MANUAL_FLOW,
        id: "global_blacklist_flow",
        globalBlacklistedEvents: ["ad_impression"],
      }),
    ]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "ad_impression");
    await page.waitForTimeout(1200);

    let spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(0);
    // Recovery check: blacklist cancel must not poison the next valid flow.
    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "checkout_step_2");
    await emitEvent(page, "checkout_step_3");
    const recovered = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(recovered.attributes, "pulse.interaction.is_error")).toBe(
      false,
    );
    spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(1);
  });

  test("local blacklisted step resets flow without terminal span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "local_blacklisted",
          name: "Local Blacklisted",
          events: [
            { name: "step_a", required: true },
            { name: "step_block", required: false, isBlacklisted: true },
            { name: "step_b", required: true },
          ],
          thresholdInMs: 500,
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "step_a");
    await emitEvent(page, "step_block");
    await emitEvent(page, "step_b");
    await page.waitForTimeout(900);

    let spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(0);
    // Regression guard: after blacklisted reset, valid skip path still completes.
    await emitEvent(page, "step_a");
    await emitEvent(page, "step_b");
    const recovered = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(recovered.attributes, "pulse.interaction.is_error")).toBe(
      false,
    );
    spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(1);
  });

  test("timeout at stage-1 (waiting for second event) emits timeout error", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await page.waitForTimeout(1000);

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  test("timeout at stage-2 (waiting for third event) emits timeout error", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "checkout_step_2");
    await page.waitForTimeout(1000);

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  test("sequence violation at stage-1 emits sequence_violation", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    // Relevant but out-of-order event (expected step_2, got step_3).
    await emitEvent(page, "checkout_step_3");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  test("sequence violation at stage-2 emits sequence_violation", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "checkout_step_2");
    // Relevant but out-of-order event (expected step_3, got step_2 again).
    await emitEvent(page, "checkout_step_2");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  test("two independent interactions each emit a span", async ({
    page,
    otlp,
  }) => {
    const singleStepConfig = [
      makeConfig({
        id: "single_step_repeatable",
        name: "Single Step Repeatable",
        events: [{ name: "checkout_step_1", required: true }],
      }),
    ];
    await seedInteractionConfig(page, singleStepConfig);
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "checkout_step_1");
    await otlp.waitForSpan("interaction", 15_000);
    await emitEvent(page, "checkout_step_1");
    await waitForInteractionCount(page, otlp, 2, 8_000);

    const spans = findAllSpans(otlp.captured, "interaction");
    expect(spans.length).toBe(2);
    expect(
      spans.every(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  test("apdex category Excellent", async ({ page, otlp }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "apdex_excellent",
          name: "Apdex Excellent",
          events: [
            { name: "ax_1", required: true },
            { name: "ax_2", required: true },
          ],
          thresholdInMs: 1000,
          uptimeLowerLimitInMs: 120,
          uptimeMidLimitInMs: 240,
          uptimeUpperLimitInMs: 420,
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "ax_1");
    await page.waitForTimeout(40);
    await emitEvent(page, "ax_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Excellent",
    );
    expect(Number(getAttr(span.attributes, "pulse.interaction.apdex_score"))).toBe(
      1,
    );
  });

  test("apdex category Good", async ({ page, otlp }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "apdex_good",
          name: "Apdex Good",
          events: [
            { name: "ag_1", required: true },
            { name: "ag_2", required: true },
          ],
          thresholdInMs: 1000,
          uptimeLowerLimitInMs: 120,
          uptimeMidLimitInMs: 240,
          uptimeUpperLimitInMs: 420,
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "ag_1");
    await page.waitForTimeout(180);
    await emitEvent(page, "ag_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Good",
    );
    const score = Number(getAttr(span.attributes, "pulse.interaction.apdex_score"));
    expect(score).toBeGreaterThan(0);
    expect(score).toBeLessThan(1);
  });

  test("apdex category Average", async ({ page, otlp }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "apdex_average",
          name: "Apdex Average",
          events: [
            { name: "aa_1", required: true },
            { name: "aa_2", required: true },
          ],
          thresholdInMs: 1200,
          uptimeLowerLimitInMs: 120,
          uptimeMidLimitInMs: 240,
          uptimeUpperLimitInMs: 420,
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "aa_1");
    await page.waitForTimeout(320);
    await emitEvent(page, "aa_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Average",
    );
    const score = Number(getAttr(span.attributes, "pulse.interaction.apdex_score"));
    expect(score).toBeGreaterThan(0);
    expect(score).toBeLessThan(1);
  });

  test("apdex category Poor", async ({ page, otlp }) => {
    await seedInteractionConfig(
      page,
      [
        makeConfig({
          id: "apdex_poor",
          name: "Apdex Poor",
          events: [
            { name: "ap_1", required: true },
            { name: "ap_2", required: true },
          ],
          thresholdInMs: 1500,
          uptimeLowerLimitInMs: 120,
          uptimeMidLimitInMs: 240,
          uptimeUpperLimitInMs: 420,
        }),
      ],
    );
    await gotoAndWaitInteractionInit(page);
    await emitEvent(page, "ap_1");
    await page.waitForTimeout(520);
    await emitEvent(page, "ap_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Poor",
    );
    expect(Number(getAttr(span.attributes, "pulse.interaction.apdex_score"))).toBe(
      0,
    );
  });

  test("complete_time nanos is consistent with span start/end nanos", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [MANUAL_FLOW]);
    await gotoAndWaitInteractionInit(page);
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await emitEvent(page, "checkout_step_1");
      await page.waitForTimeout(80);
      await emitEvent(page, "checkout_step_2");
      await page.waitForTimeout(80);
      await emitEvent(page, "checkout_step_3");
      try {
        await waitForInteractionCount(page, otlp, 1, 4_000);
        break;
      } catch {
        // Retry when initial flow events race startup in CI.
      }
    }

    const span = findAllSpans(otlp.captured, "interaction")[0];
    expect(span).toBeDefined();
    if (span == null) return;
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
    await gotoAndWaitInteractionInit(page);
    await otlp.waitForLog("session.start", 10_000);
    otlp.reset();

    await emitEvent(page, "checkout_step_1");
    await emitEvent(page, "checkout_step_2");
    await emitEvent(page, "checkout_step_3");
    await page.waitForTimeout(2000);
    expect(findAllSpans(otlp.captured, "interaction").length).toBe(0);
    // SDK should still emit custom event logs when interaction feature is unavailable.
    await otlp.waitForLogByBody("checkout_step_3", 10_000);
  });
});
