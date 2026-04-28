/**
 * Manual TC verification — Navigation Instrumentation
 * Uses the OTLP fixture (network interception) — no CH ingest latency.
 */
import { test, expect, getAttr, findAllSpansByName, findAllLogsByBody } from "./fixture";

test.describe("Manual TC verification — Navigation Instrumentation", () => {

  test("TC1 — screen_load emitted with correct attrs on page load", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/products");
    const span = await otlp.waitForSpanByName("screen_load");
    expect(getAttr(span.attributes, "pulse.type")).toBe("screen_load");
    expect(getAttr(span.attributes, "start.type")).toBe("cold");
    expect(getAttr(span.attributes, "navigation.type")).toBe("navigate");
    expect(Number(getAttr(span.attributes, "load.duration_ms"))).toBeGreaterThan(0);
    expect(Number(getAttr(span.attributes, "ttfb_ms"))).toBeGreaterThanOrEqual(0);
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    console.log("TC1 PASS");
  });

  test("TC2 — screen_interactive emitted with tti", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/products");
    const span = await otlp.waitForSpanByName("screen_interactive");
    expect(getAttr(span.attributes, "pulse.type")).toBe("screen_interactive");
    expect(Number(getAttr(span.attributes, "tti"))).toBeGreaterThan(0);
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    console.log("TC2 PASS");
  });

  test("TC3 — start.type=reload on hard reload", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/products");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.reload();
    const span = await otlp.waitForSpanByName("screen_load");
    expect(getAttr(span.attributes, "start.type")).toBe("reload");
    expect(getAttr(span.attributes, "navigation.type")).toBe("reload");
    console.log("TC3 PASS");
  });

  test("TC4 — start.type=back_forward on browser back", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.goto("http://localhost:3002/products");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.goBack();
    const span = await otlp.waitForSpanByName("screen_load");
    expect(getAttr(span.attributes, "start.type")).toBe("back_forward");
    expect(getAttr(span.attributes, "navigation.type")).toBe("back_forward");
    console.log("TC4 PASS");
  });

  test("TC5 — SPA navigation emits screen_session for the route that ended", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.click("text=Products");
    const span = await otlp.waitForSpanByName("screen_session");
    expect(getAttr(span.attributes, "pulse.type")).toBe("screen_session");
    // screen.name should be "/" — the route that just ended
    expect(getAttr(span.attributes, "screen.name")).toBe("/");
    expect(getAttr(span.attributes, "url.path")).toBe("/");
    expect(Number(getAttr(span.attributes, "session.duration"))).toBeGreaterThan(0);
    console.log("TC5 PASS");
  });

  test("TC6 — previous_screen.name correct on second navigation", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.click("text=Products");
    await otlp.waitForSpanByName("screen_session");
    otlp.reset();
    await page.click("text=Cart");
    const span = await otlp.waitForSpanByName("screen_session");
    expect(getAttr(span.attributes, "screen.name")).toBe("/products");
    expect(getAttr(span.attributes, "previous_screen.name")).toBe("/");
    console.log("TC6 PASS");
  });

  test("TC8 — heuristic strips numeric ID from /products/123", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    // Nav to /products/123 — emits session for "/" (skip it)
    await page.evaluate(() => history.pushState({}, "", "/products/123"));
    await otlp.waitForSpanByName("screen_session"); // "/" session
    otlp.reset();
    // Nav away — emits session for "/products/123"
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    const span = await otlp.waitForSpanByName("screen_session");
    const screenName = getAttr(span.attributes, "screen.name");
    expect(screenName).toBe("/products");
    console.log("TC8 PASS: screen.name=", screenName);
  });

  test("TC9 — heuristic strips UUID segment", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    // Nav to UUID route — emits session for "/" (skip it)
    await page.evaluate(() => history.pushState({}, "", "/orders/550e8400-e29b-41d4-a716-446655440000"));
    await otlp.waitForSpanByName("screen_session"); // "/" session
    otlp.reset();
    // Nav away — emits session for UUID route
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    const span = await otlp.waitForSpanByName("screen_session");
    const screenName = getAttr(span.attributes, "screen.name");
    expect(screenName).toBe("/orders");
    console.log("TC9 PASS: screen.name=", screenName);
  });

  test("TC14 — url.path on screen_load and screen_interactive", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/products");
    const load = await otlp.waitForSpanByName("screen_load");
    const interactive = await otlp.waitForSpanByName("screen_interactive");
    expect(getAttr(load.attributes, "url.path")).toBe("/products");
    expect(getAttr(interactive.attributes, "url.path")).toBe("/products");
    console.log("TC14 PASS");
  });

  test("TC15 — sub-100ms navigation does NOT emit screen_session for /fast-a", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    // Immediate consecutive pushState — /fast-a will have < 1ms duration
    await page.evaluate(() => {
      history.pushState({}, "", "/fast-a");
      history.pushState({}, "", "/fast-b");
    });
    // Wait for /fast-b session to appear (proves we're past ingest point)
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await page.waitForTimeout(500);
    const spans = findAllSpansByName(otlp.captured, "screen_session");
    const fastA = spans.filter(s => getAttr(s.attributes, "url.path") === "/fast-a");
    expect(fastA).toHaveLength(0);
    console.log("TC15 PASS: no session for sub-100ms /fast-a");
  });

  test("TC16 — replaceState does NOT create new session", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.evaluate(() => history.pushState({}, "", "/checkout"));
    await page.waitForTimeout(200);
    await page.evaluate(() => history.replaceState({}, "", "/checkout?step=2"));
    await page.waitForTimeout(200);
    await page.evaluate(() => history.pushState({}, "", "/confirm"));
    // Wait for /checkout session to appear
    await otlp.waitForSpanByName("screen_session");
    await page.waitForTimeout(300);
    const spans = findAllSpansByName(otlp.captured, "screen_session");
    const replaceSession = spans.filter(s => getAttr(s.attributes, "url.path") === "/checkout?step=2");
    expect(replaceSession).toHaveLength(0);
    console.log("TC16 PASS: no session for replaceState URL");
  });

  test("TC17 — same-route pushState does NOT split session", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(200);
    await page.evaluate(() => history.pushState({}, "", "/products")); // same route
    await page.waitForTimeout(200);
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    // Wait for the /products session
    await otlp.waitForSpanByName("screen_session");
    await page.waitForTimeout(300);
    const spans = findAllSpansByName(otlp.captured, "screen_session");
    const productSessions = spans.filter(s => getAttr(s.attributes, "url.path") === "/products");
    expect(productSessions).toHaveLength(1);
    console.log("TC17 PASS: only 1 screen_session for /products");
  });

  test("TC18 — hash-only change does NOT split session", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForSpanByName("screen_load");
    otlp.reset();
    await page.evaluate(() => history.pushState({}, "", "/products"));
    await page.waitForTimeout(200);
    await page.evaluate(() => history.pushState({}, "", "/products#section")); // same pathname
    await page.waitForTimeout(200);
    await page.evaluate(() => history.pushState({}, "", "/cart"));
    await otlp.waitForSpanByName("screen_session");
    await page.waitForTimeout(300);
    const spans = findAllSpansByName(otlp.captured, "screen_session");
    const productSessions = spans.filter(s => getAttr(s.attributes, "url.path") === "/products");
    expect(productSessions).toHaveLength(1);
    console.log("TC18 PASS: only 1 session for /products (hash ignored)");
  });

  test("TC22 — device.app.lifecycle created on page load", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    const log = await otlp.waitForLogByBody("device.app.lifecycle");
    expect(getAttr(log.attributes, "app.state")).toBe("created");
    console.log("TC22 PASS");
  });

  test("TC23+24 — background then foreground lifecycle events", async ({ page, otlp }) => {
    await page.goto("http://localhost:3002/");
    await otlp.waitForLogByBody("device.app.lifecycle");
    otlp.reset();

    // Simulate tab going background
    await page.evaluate(() => {
      Object.defineProperty(document, "hidden", { value: true, configurable: true });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    const bgLog = await otlp.waitForLogByBody("device.app.lifecycle");
    expect(getAttr(bgLog.attributes, "app.state")).toBe("background");
    console.log("TC23 PASS: background verified");
    otlp.reset();

    // Simulate tab coming back to foreground
    await page.evaluate(() => {
      Object.defineProperty(document, "hidden", { value: false, configurable: true });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    const fgLog = await otlp.waitForLogByBody("device.app.lifecycle");
    expect(getAttr(fgLog.attributes, "app.state")).toBe("foreground");
    console.log("TC24 PASS: foreground verified");
  });

});
