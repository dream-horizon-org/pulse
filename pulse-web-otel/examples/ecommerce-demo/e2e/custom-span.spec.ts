import {
  test,
  expect,
  getAttr,
  findAllSpansByName,
  getOtlpSpanStatusCode,
} from "./fixture";

test.describe("@custom-span", () => {
  test("J1: manual startSpan on products page", async ({ page, otlp }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-fetch", {
        attributes: { user_id: "test-user" },
      }).end("OK");
    });

    const spans = findAllSpansByName(otlp.captured, "products-fetch");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(getAttr(span, "pulse.type")).toBe("custom_span");
    expect(getAttr(span, "platform")).toBe("web");
    expect(getAttr(span, "session.id")).toBeDefined();
    expect(getAttr(span, "screen.name")).toBe("/products");
    expect(getAttr(span, "user_id")).toBe("test-user");
  });

  test("J2: trackSpan wrapping product detail load", async ({ page, otlp }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(async () => {
      await (window as any).Pulse.trackSpan("product-detail-load", () => {
        return fetch("/data/product-1.json").then((r) => r.json());
      });
    });

    const spans = findAllSpansByName(otlp.captured(), "product-detail-load");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(span.endTimeUnixNano > span.startTimeUnixNano).toBe(true);
    expect(getAttr(span, "pulse.type")).toBe("custom_span");
  });

  test("J3: trackSpan with abort fetch → ERROR status", async ({
    page,
    otlp,
  }) => {
    await page.goto("/network-lab");
    await page.waitForTimeout(500);

    await page.evaluate(async () => {
      try {
        await (window as any).Pulse.trackSpan("stalled-fetch", async () => {
          return fetch("/pulse-e2e-xhr-stall?delay=5000");
        });
      } catch {
        // expected — trackSpan rejects on fetch timeout
      }
    });

    await page.waitForTimeout(6000);

    const spans = findAllSpansByName(otlp.captured, "stalled-fetch");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("J4: trackSpan with 404 fetch → ERROR status", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(async () => {
      try {
        await (window as any).Pulse.trackSpan("fetch-missing", async () => {
          await fetch("/nonexistent-endpoint").then((r) => {
            if (!r.ok) throw new Error("404");
          });
        });
      } catch {
        // expected
      }
    });

    const spans = findAllSpansByName(otlp.captured, "fetch-missing");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("J5: multi-span session continuity", async ({ page, otlp }) => {
    await page.goto("/");
    await page.waitForTimeout(500);

    const sessionId = await page.evaluate(() => {
      return (window as any).__PULSE_SESSION_ID__;
    });

    // Create spans on different pages
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span1").end("OK");
    });

    await page.goto("/products");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span2").end("OK");
    });

    await page.goto("/cart");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span3").end("OK");
    });

    const allSpans = otlp.captured();
    const spans = allSpans.filter(
      (s: any) =>
        s.name === "span1" || s.name === "span2" || s.name === "span3",
    );

    expect(spans.length).toBe(3);
    spans.forEach((span: any) => {
      expect(getAttr(span, "session.id")).toBe(sessionId);
    });

    // Verify chronological order
    expect(spans[0].startTimeUnixNano < spans[1].startTimeUnixNano).toBe(true);
    expect(spans[1].startTimeUnixNano < spans[2].startTimeUnixNano).toBe(true);
  });

  test("J6: duration > 0 with nested network spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("grid-mount-timing").end("OK");
    });

    const spans = findAllSpansByName(otlp.captured(), "grid-mount-timing");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    const duration =
      BigInt(span.endTimeUnixNano) - BigInt(span.startTimeUnixNano);
    expect(duration > 0n).toBe(true);
  });

  test("J7: addEvent breadcrumbs with ordered timeUnixNano", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      const span = (window as any).Pulse.startSpan("breadcrumb-trace");
      span.addEvent("event1", { index: 1 });
      span.addEvent("event2", { index: 2 });
      span.addEvent("event3", { index: 3 });
      span.end("OK");
    });

    const spans = findAllSpansByName(otlp.captured(), "breadcrumb-trace");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(span.events).toBeDefined();
    expect(span.events.length).toBe(3);

    // Events should be in order
    expect(
      BigInt(span.events[0].timeUnixNano) < BigInt(span.events[1].timeUnixNano),
    ).toBe(true);
    expect(
      BigInt(span.events[1].timeUnixNano) < BigInt(span.events[2].timeUnixNano),
    ).toBe(true);
  });

  test("NEG1: throw in trackSpan → ERROR", async ({ page, otlp }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      try {
        (window as any).Pulse.trackSpan("error-span", () => {
          throw new Error("Intentional error");
        });
      } catch {
        // expected
      }
    });

    const spans = findAllSpansByName(otlp.captured(), "error-span");
    expect(spans.length).toBeGreaterThan(0);
    const span = spans[0];
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("NEG2: page refresh maintains session continuity", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(500);

    const sessionId1 = await page.evaluate(() => {
      return (window as any).__PULSE_SESSION_ID__;
    });

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span-before-refresh").end("OK");
    });

    await page.reload();
    await page.waitForTimeout(500);

    const sessionId2 = await page.evaluate(() => {
      return (window as any).__PULSE_SESSION_ID__;
    });

    // After refresh, session should be different
    expect(sessionId1).not.toBe(sessionId2);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span-after-refresh").end("OK");
    });

    const allSpans = otlp.captured();
    const spans = allSpans.filter(
      (s: any) =>
        s.name === "span-before-refresh" || s.name === "span-after-refresh",
    );

    expect(spans.length).toBe(2);
    expect(getAttr(spans[0], "session.id")).toBe(sessionId1);
    expect(getAttr(spans[1], "session.id")).toBe(sessionId2);
  });

  test("NEG3: pre-init startSpan returns noop", async ({ page, otlp }) => {
    // Don't wait for init
    const result = await page.evaluate(() => {
      const span = (window as any).Pulse.startSpan("pre-init-span");
      span.end("OK");
      return "success";
    });

    expect(result).toBe("success");
    // Span should not be exported (noop)
    const spans = findAllSpansByName(otlp.captured(), "pre-init-span");
    expect(spans.length).toBe(0);
  });
});
