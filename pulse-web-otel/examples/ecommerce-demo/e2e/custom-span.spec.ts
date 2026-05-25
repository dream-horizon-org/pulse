import type { Page } from "@playwright/test";
import {
  test,
  expect,
  getAttr,
  findAllSpansByName,
  getOtlpSpanStatusCode,
} from "./fixture";

async function waitForPulseInitialized(page: Page): Promise<void> {
  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const w = window as unknown as {
            Pulse?: { isInitialized: () => boolean };
          };
          return w.Pulse?.isInitialized?.() ?? false;
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

/** Align with m4-network — forceFlush via synthetic pagehide. */
async function flushTraceExport(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );
  });
  await page.waitForTimeout(400);
}

test.describe("@custom-span", () => {
  test("J1: manual startSpan on products page", async ({ page, otlp }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-fetch", {
        attributes: { user_id: "test-user" },
      }).end("OK");
    });

    const span = await otlp.waitForSpanByName("products-fetch", 8_000);
    expect(getAttr(span.attributes, "pulse.type")).toBe("custom_span");
    expect(getResourcePlatform(otlp.captured)).toBe("web");
    expect(getAttr(span.attributes, "session.id")).toBeDefined();
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    expect(getAttr(span.attributes, "user_id")).toBe("test-user");
  });

  test("J2: trackSpan wrapping product detail load", async ({ page, otlp }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.locator('[data-testid="product-card"]').first().click();
    await page.waitForURL("**/products/1");

    await page.evaluate(async () => {
      await (window as any).Pulse.trackSpan("product-detail-load", async () => {
        const response = await fetch("/api/product-detail.json?id=1");
        return response.json();
      });
    });

    const span = await otlp.waitForSpanByName("product-detail-load", 8_000);
    expect(
      BigInt(span.endTimeUnixNano ?? "0") >
        BigInt(span.startTimeUnixNano ?? "0"),
    ).toBe(true);
    expect(getAttr(span.attributes, "pulse.type")).toBe("custom_span");
    expect(getAttr(span.attributes, "screen.name")).toBe("/products/1");
  });

  test("J3: trackSpan with abort fetch → ERROR status", async ({
    page,
    otlp,
  }) => {
    await page.goto("/network-lab");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      try {
        await (window as any).Pulse.trackSpan("stalled-fetch", async () => {
          const controller = new AbortController();
          setTimeout(() => controller.abort(), 600);
          await fetch("/pulse-e2e-xhr-stall", { signal: controller.signal });
        });
      } catch {
        // expected — trackSpan rejects on abort
      }
    });

    const span = await otlp.waitForSpanByName("stalled-fetch", 8_000);
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("J4: trackSpan with 404 fetch → ERROR status", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/api/does-not-exist.json"),
      async (route) => {
        await route.fulfill({
          status: 404,
          headers: { "Content-Type": "application/json" },
          body: '{"error":"not_found"}',
        });
      },
    );

    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      try {
        await (window as any).Pulse.trackSpan("fetch-missing", async () => {
          const response = await fetch("/api/does-not-exist.json");
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
          }
        });
      } catch {
        // expected
      }
    });

    const span = await otlp.waitForSpanByName("fetch-missing", 8_000);
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("J5: multi-span session continuity", async ({ page, otlp }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    const startLog = await otlp.waitForLog("session.start", 15_000);
    const sessionId = getAttr(startLog.attributes, "session.id") as string;
    expect(sessionId).toBeTruthy();

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span1").end("OK");
    });

    await page.goto("/products");
    await waitForPulseInitialized(page);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span2").end("OK");
    });

    await page.goto("/cart");
    await waitForPulseInitialized(page);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span3").end("OK");
    });

    await flushTraceExport(page);

    const spans = ["span1", "span2", "span3"].map(
      (name) => findAllSpansByName(otlp.captured, name)[0],
    );
    expect(spans.every(Boolean)).toBe(true);
    expect(spans.length).toBe(3);
    for (const span of spans) {
      expect(span).toBeDefined();
      expect(getAttr(span!.attributes, "session.id")).toBe(sessionId);
    }

    expect(
      BigInt(spans[0]!.startTimeUnixNano ?? "0") <
        BigInt(spans[1]!.startTimeUnixNano ?? "0"),
    ).toBe(true);
    expect(
      BigInt(spans[1]!.startTimeUnixNano ?? "0") <
        BigInt(spans[2]!.startTimeUnixNano ?? "0"),
    ).toBe(true);
  });

  test("J6: duration > 0 with nested network spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(() => {
      (window as any).__gridMountSpan = (window as any).Pulse.startSpan(
        "grid-mount-timing",
      );
    });
    await page.locator('[data-testid="product-card"]').first().waitFor();
    await page.evaluate(() => {
      (window as any).__gridMountSpan?.end("OK");
    });

    const span = await otlp.waitForSpanByName("grid-mount-timing", 8_000);
    const duration =
      BigInt(span.endTimeUnixNano ?? "0") -
      BigInt(span.startTimeUnixNano ?? "0");
    expect(duration > 0n).toBe(true);
  });

  test("J7: addEvent breadcrumbs with ordered timeUnixNano", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      const span = (window as any).Pulse.startSpan("breadcrumb-trace");
      span.addEvent("event1", { index: 1 });
      await new Promise((resolve) => setTimeout(resolve, 5));
      span.addEvent("event2", { index: 2 });
      await new Promise((resolve) => setTimeout(resolve, 5));
      span.addEvent("event3", { index: 3 });
      span.end("OK");
    });

    const span = await otlp.waitForSpanByName("breadcrumb-trace", 8_000);
    expect(span.events).toBeDefined();
    expect(span.events!.length).toBe(3);

    expect(
      BigInt(span.events![0]!.timeUnixNano ?? "0") <
        BigInt(span.events![1]!.timeUnixNano ?? "0"),
    ).toBe(true);
    expect(
      BigInt(span.events![1]!.timeUnixNano ?? "0") <
        BigInt(span.events![2]!.timeUnixNano ?? "0"),
    ).toBe(true);
  });

  test("NEG1: throw in trackSpan → ERROR", async ({ page, otlp }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      try {
        await (window as any).Pulse.trackSpan("error-span", () => {
          throw new Error("Intentional error");
        });
      } catch {
        // expected
      }
    });

    const span = await otlp.waitForSpanByName("error-span", 8_000);
    expect(getOtlpSpanStatusCode(span)).toBe(2); // ERROR
  });

  test("NEG2: page refresh maintains session continuity", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    const startLog = await otlp.waitForLog("session.start", 15_000);
    const sessionId1 = getAttr(startLog.attributes, "session.id") as string;

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span-before-refresh").end("OK");
    });
    await flushTraceExport(page);

    await page.reload();
    await waitForPulseInitialized(page);
    await page.waitForTimeout(500);

    const sessionId2 = await page.evaluate(() => {
      return window.localStorage.getItem("pulse_session_id");
    });

    expect(sessionId2).toBe(sessionId1);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("span-after-refresh").end("OK");
    });

    await flushTraceExport(page);

    const before = findAllSpansByName(otlp.captured, "span-before-refresh")[0];
    const after = findAllSpansByName(otlp.captured, "span-after-refresh")[0];
    expect(before).toBeDefined();
    expect(after).toBeDefined();
    expect(getAttr(before!.attributes, "session.id")).toBe(sessionId1);
    expect(getAttr(after!.attributes, "session.id")).toBe(sessionId1);
  });

  test("NEG3: pre-init startSpan returns noop", async ({ page, otlp }) => {
    await page.goto("/", { waitUntil: "commit" });

    const result = await page.evaluate(() => {
      const span = (window as any).Pulse?.startSpan?.("pre-init-span");
      span?.end?.("OK");
      return "success";
    });

    expect(result).toBe("success");
    await page.waitForTimeout(500);
    expect(findAllSpansByName(otlp.captured, "pre-init-span").length).toBe(0);
  });
});

function getResourcePlatform(
  captured: Parameters<typeof findAllSpansByName>[0],
): string | undefined {
  for (const item of captured) {
    if (item.type !== "traces") continue;
    for (const resourceSpan of item.body.resourceSpans ?? []) {
      const platform = getAttr(resourceSpan.resource?.attributes, "platform");
      if (platform !== undefined) return String(platform);
    }
  }
  return undefined;
}
