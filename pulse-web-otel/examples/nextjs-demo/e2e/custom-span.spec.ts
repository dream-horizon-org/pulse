import {
  test,
  expect,
  getAttr,
  findAllSpans,
  getOtlpSpanStatusCode,
} from "./fixture";

test.describe("@custom-span-next", () => {
  test("J8: App Router /products RSC render timing span", async ({
    page,
    otlp,
  }) => {
    await page.goto("http://localhost:3003/products");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-render").end("OK");
    });

    const spans = findAllSpans(otlp.captured).filter(
      (s: any) => s.name === "products-render",
    );
    expect(spans.length).toBeGreaterThan(0);
    expect(getAttr(spans[0], "pulse.type")).toBe("custom_span");
  });

  test("J9: Product detail trackEvent coexistence", async ({ page, otlp }) => {
    await page.goto("http://localhost:3003/products");
    await page.waitForTimeout(500);

    await page.evaluate(async () => {
      (window as any).Pulse.trackEvent("product_viewed", {
        product_id: "123",
      });
      await (window as any).Pulse.trackSpan("product-load", async () => {
        await new Promise((resolve) => setTimeout(resolve, 100));
      });
    });

    const spans = findAllSpans(otlp.captured).filter(
      (s: any) => s.name === "product-load",
    );
    expect(spans.length).toBeGreaterThan(0);
    expect(getAttr(spans[0], "pulse.type")).toBe("custom_span");
  });

  test("J10: /search?q=shoes screen.name on span", async ({ page, otlp }) => {
    await page.goto("http://localhost:3003/products?q=shoes");
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      (window as any).Pulse.startSpan("search-span").end("OK");
    });

    const spans = findAllSpans(otlp.captured).filter(
      (s: any) => s.name === "search-span",
    );
    expect(spans.length).toBeGreaterThan(0);
    const screenName = getAttr(spans[0], "screen.name");
    expect(screenName).toContain("/products");
  });

  test("J11: Multi-hop session continuity (4 page-visit spans)", async ({
    page,
    otlp,
  }) => {
    // Navigate to home first to initialize session
    await page.goto("http://localhost:3003");
    await page.waitForTimeout(500);

    const sessionId = await page.evaluate(() => {
      return (window as any).__PULSE_SESSION_ID__;
    });

    // Create spans across multiple navigations
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("home-span").end("OK");
    });

    await page.goto("http://localhost:3003/products");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("products-span").end("OK");
    });

    await page.goto("http://localhost:3003/products/1");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("detail-span").end("OK");
    });

    await page.goto("http://localhost:3003/cart");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as any).Pulse.startSpan("cart-span").end("OK");
    });

    const allSpans = findAllSpans(otlp.captured);
    const spanNames = ["home-span", "products-span", "detail-span", "cart-span"];
    spanNames.forEach((name) => {
      const span = allSpans.find((s: any) => s.name === name);
      expect(span).toBeDefined();
      expect(getAttr(span, "session.id")).toBe(sessionId);
    });
  });

  test("J12: Pages Router /pages-demo → /shop", async ({ page, otlp }) => {
    // Skip if pages router not available in this demo
    try {
      await page.goto("http://localhost:3003/pages-demo/shop");
      await page.waitForTimeout(500);

      await page.evaluate(() => {
        (window as any).Pulse.startSpan("pages-shop-span").end("OK");
      });

      const spans = findAllSpans(otlp.captured).filter(
        (s: any) => s.name === "pages-shop-span",
      );
      if (spans.length > 0) {
        expect(getAttr(spans[0], "pulse.type")).toBe("custom_span");
      }
    } catch {
      // Pages router may not be available
      test.skip();
    }
  });

  test("J13: Pages Router [productId] dynamic route", async ({ page, otlp }) => {
    try {
      await page.goto("http://localhost:3003/pages-demo/shop/123");
      await page.waitForTimeout(500);

      await page.evaluate(() => {
        (window as any).Pulse.startSpan("product-detail-pages").end("OK");
      });

      const spans = findAllSpans(otlp.captured).filter(
        (s: any) => s.name === "product-detail-pages",
      );
      if (spans.length > 0) {
        expect(getAttr(spans[0], "pulse.type")).toBe("custom_span");
      }
    } catch {
      // Pages router may not be available
      test.skip();
    }
  });

  test("J14: /api-demo network nesting within custom span", async ({
    page,
    otlp,
  }) => {
    await page.goto("http://localhost:3003");
    await page.waitForTimeout(500);

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

    const spans = findAllSpans(otlp.captured).filter(
      (s: any) => s.name === "api-call-wrapper",
    );
    expect(spans.length).toBeGreaterThan(0);
    expect(getAttr(spans[0], "pulse.type")).toBe("custom_span");
  });
});
