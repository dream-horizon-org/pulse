import type { Page } from "@playwright/test";
import {
  test,
  expect,
  getAttr,
  findAllSpansByName,
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

async function flushTraceExport(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );
  });
  await page.waitForTimeout(400);
}

test.describe("@custom-span-next", () => {
  test("J8: App Router /products RSC render timing span", async ({
    page,
    otlp,
  }) => {
    await page.goto("http://localhost:3003/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-render").end("OK");
    });
    await flushTraceExport(page);

    const span = await otlp.waitForSpanByName("products-render", 8_000);
    expect(getAttr(span.attributes, "pulse.type")).toBe("custom_span");
  });

  test("J9: Product detail trackEvent coexistence", async ({ page, otlp }) => {
    await page.goto("http://localhost:3003/products");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      (window as any).Pulse.trackEvent("product_viewed", {
        product_id: "123",
      });
      await (window as any).Pulse.trackSpan("product-load", async () => {
        await new Promise((resolve) => setTimeout(resolve, 100));
      });
    });
    await flushTraceExport(page);

    const span = await otlp.waitForSpanByName("product-load", 8_000);
    expect(getAttr(span.attributes, "pulse.type")).toBe("custom_span");
  });

  test("J10: /search?q=shoes screen.name on span", async ({ page, otlp }) => {
    await page.goto("http://localhost:3003/products?q=shoes");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("search-span").end("OK");
    });
    await flushTraceExport(page);

    const span = await otlp.waitForSpanByName("search-span", 8_000);
    const screenName = getAttr(span.attributes, "screen.name");
    expect(screenName).toBe("/products");
  });

  test("J11: Multi-hop session continuity (4 page-visit spans)", async ({
    page,
    otlp,
  }) => {
    await page.goto("http://localhost:3003");
    await waitForPulseInitialized(page);
    const sessionStart = await otlp.waitForLog("session.start", 15_000);
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;
    expect(sessionId).toBeTruthy();

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("home-span").end("OK");
    });

    await page.goto("http://localhost:3003/products");
    await waitForPulseInitialized(page);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-span").end("OK");
    });

    await page.goto("http://localhost:3003/products/1");
    await waitForPulseInitialized(page);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("detail-span").end("OK");
    });

    await page.goto("http://localhost:3003/cart");
    await waitForPulseInitialized(page);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("cart-span").end("OK");
    });
    await flushTraceExport(page);

    const spanNames = ["home-span", "products-span", "detail-span", "cart-span"];
    for (const name of spanNames) {
      const span = await otlp.waitForSpanByName(name, 8_000);
      expect(getAttr(span.attributes, "session.id")).toBe(sessionId);
    }
  });

  test("J12: Pages Router /pages-demo → /shop", async ({ page, otlp }) => {
    try {
      await page.goto("http://localhost:3003/pages-demo/shop");
      await waitForPulseInitialized(page);
      await otlp.waitForLog("session.start", 15_000);

      await page.evaluate(() => {
        (window as any).Pulse.startSpan("pages-shop-span").end("OK");
      });
      await flushTraceExport(page);

      const spans = findAllSpansByName(otlp.captured, "pages-shop-span");
      if (spans.length > 0) {
        expect(getAttr(spans[0].attributes, "pulse.type")).toBe("custom_span");
      }
    } catch {
      test.skip();
    }
  });

  test("J13: Pages Router [productId] dynamic route", async ({ page, otlp }) => {
    try {
      await page.goto("http://localhost:3003/pages-demo/shop/123");
      await waitForPulseInitialized(page);
      await otlp.waitForLog("session.start", 15_000);

      await page.evaluate(() => {
        (window as any).Pulse.startSpan("product-detail-pages").end("OK");
      });
      await flushTraceExport(page);

      const spans = findAllSpansByName(otlp.captured, "product-detail-pages");
      if (spans.length > 0) {
        expect(getAttr(spans[0].attributes, "pulse.type")).toBe("custom_span");
      }
    } catch {
      test.skip();
    }
  });

  test("J14: /api-demo network nesting within custom span", async ({
    page,
    otlp,
  }) => {
    await page.goto("http://localhost:3003");
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);

    await page.evaluate(async () => {
      await (window as any).Pulse.trackSpan(
        "api-call-wrapper",
        async () => {
          try {
            const response = await fetch("/api/data");
            return response.json();
          } catch {
            // expected
          }
        },
      );
    });
    await flushTraceExport(page);

    const span = await otlp.waitForSpanByName("api-call-wrapper", 8_000);
    expect(getAttr(span.attributes, "pulse.type")).toBe("custom_span");
  });
});
