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

  test("apdex scoring works for 3-event interaction durations", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "apdex_three_step",
        name: "Apdex Three Step",
        events: [
          { name: "a1", required: true },
          { name: "a2", required: true },
          { name: "a3", required: true },
        ],
        thresholdInMs: 1200,
        uptimeLowerLimitInMs: 150,
        uptimeMidLimitInMs: 300,
        uptimeUpperLimitInMs: 450,
      }),
    ]);
    await gotoAndWaitInteractionInit(page);

    const t0 = Date.now();
    // Excellent (end-start = 80, clearly below lower=150)
    await emitEvent(page, "a1", undefined, t0);
    await emitEvent(page, "a2", undefined, t0 + 40);
    await emitEvent(page, "a3", undefined, t0 + 80);
    // Good (end-start = 280)
    await emitEvent(page, "a1", undefined, t0 + 1_000);
    await emitEvent(page, "a2", undefined, t0 + 1_120);
    await emitEvent(page, "a3", undefined, t0 + 1_280);
    // Poor (end-start > upper)
    await emitEvent(page, "a1", undefined, t0 + 2_000);
    await emitEvent(page, "a2", undefined, t0 + 2_260);
    await emitEvent(page, "a3", undefined, t0 + 2_520);
    await waitForInteractionCount(page, otlp, 3, 12_000);

    const spans = findAllSpans(otlp.captured, "interaction").filter(
      (s) =>
        getAttr(s.attributes, "pulse.interaction.config.id") === "apdex_three_step",
    );
    expect(spans.length).toBe(3);
    const categories = spans.map((span) =>
      String(getAttr(span.attributes, "pulse.interaction.user_category")),
    );
    expect(categories).toContain("Excellent");
    expect(categories).toContain("Good");
    expect(categories).toContain("Poor");
  });

  test("shared prefix branching: e1,e2,e4 is non-terminal; e1,e2,e5 and e1,e2,e3 terminal", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeConfig({
        id: "branch_e123",
        name: "Branch E123",
        events: [
          { name: "e1", required: true },
          { name: "e2", required: true },
          { name: "e3", required: true },
        ],
        thresholdInMs: 5000,
      }),
      makeConfig({
        id: "branch_e125",
        name: "Branch E125",
        events: [
          { name: "e1", required: true },
          { name: "e2", required: true },
          { name: "e5", required: true },
        ],
        thresholdInMs: 5000,
      }),
    ]);
    await gotoAndWaitInteractionInit(page);

    // Shared prefix + irrelevant e4 should not terminal.
    await emitEvent(page, "e1");
    await emitEvent(page, "e2");
    await emitEvent(page, "e4");
    await page.waitForTimeout(500);
    expect(findAllSpans(otlp.captured, "interaction").length).toBe(0);

    // e5 finalizes second branch despite intermediate irrelevant e4.
    await emitEvent(page, "e5");
    await waitForInteractionCount(page, otlp, 1, 8_000);

    let spans = findAllSpans(otlp.captured, "interaction");
    let branch123 = spans.filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "branch_e123",
    );
    let branch125 = spans.filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "branch_e125",
    );
    expect(branch125.length).toBeGreaterThanOrEqual(1);
    expect(
      branch125.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);

    // Fresh second run: e1,e2,e3 finalizes other branch.
    otlp.reset();
    await emitEvent(page, "e1");
    await emitEvent(page, "e2");
    await emitEvent(page, "e3");
    await waitForInteractionCount(page, otlp, 1, 8_000);
    spans = findAllSpans(otlp.captured, "interaction");
    branch123 = spans.filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "branch_e123",
    );
    branch125 = spans.filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "branch_e125",
    );
    expect(branch123.length).toBeGreaterThanOrEqual(1);
    expect(
      branch123.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });
});
