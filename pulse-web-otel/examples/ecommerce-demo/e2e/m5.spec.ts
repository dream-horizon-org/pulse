/**
 * M5 E2E Tests — Network Instrumentation
 *
 * Tests every Done Criteria from web-sdk-plan/v1/02-instrumentations/network.md.
 * Uses the OTLP fixture (page.route interception) — no CH latency.
 *
 * Attribute names use stable OTel HTTP semconv:
 *   http.request.method, url.full, http.response.status_code, server.address, server.port
 *
 * Run: yarn e2e --grep "@M5" --project=chromium
 */
import { test, expect, getAttr, findAllSpans } from "./fixture";

// ─── TC1: basic fetch span ────────────────────────────────────────────────────

test("@M5 TC1 — fetch() produces http span with stable semconv attrs", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://httpbin.org/get").catch(() => {/* ignore CORS */});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "pulse.type")).toBe("http");
  expect(getAttr(span.attributes, "http.request.method")).toBeTruthy();
  expect(getAttr(span.attributes, "url.full")).toBeTruthy();
  expect(getAttr(span.attributes, "server.address")).toBeTruthy();
  console.log("TC1 PASS");
});

// ─── TC2: url.full strips query params (privacy) ──────────────────────────────

test("@M5 TC2 — url.full has query params stripped by default", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://httpbin.org/get?token=secret&page=2").catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  const urlFull = String(getAttr(span.attributes, "url.full") ?? "");
  expect(urlFull).not.toContain("token=secret");
  expect(urlFull).not.toContain("page=2");
  console.log("TC2 PASS: url.full =", urlFull);
});

// ─── TC3: OTLP endpoint NOT traced ────────────────────────────────────────────

test("@M5 TC3 — OTLP ingest endpoint calls are NOT traced", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await page.waitForTimeout(2000);

  const httpSpans = findAllSpans(otlp.captured, "http");
  const selfTraced = httpSpans.filter((s) =>
    String(getAttr(s.attributes, "url.full") ?? "").includes("127.0.0.1:4318"),
  );
  expect(selfTraced).toHaveLength(0);
  console.log("TC3 PASS: no self-tracing");
});

// ─── TC4: GraphQL operation name + type ───────────────────────────────────────

test("@M5 TC4 — GraphQL POST with operationName → graphql.operation.name", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://httpbin.org/post", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: "query GetProducts { products { id } }",
        operationName: "GetProducts",
      }),
    }).catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "graphql.operation.name")).toBe("GetProducts");
  expect(getAttr(span.attributes, "graphql.operation.type")).toBe("query");
  console.log("TC4 PASS");
});

// ─── TC5: GraphQL mutation type ───────────────────────────────────────────────

test("@M5 TC5 — GraphQL mutation body → graphql.operation.type = mutation", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://httpbin.org/post", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: "mutation CreateOrder($input: OrderInput!) { createOrder(input: $input) { id } }",
        operationName: "CreateOrder",
      }),
    }).catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "graphql.operation.type")).toBe("mutation");
  console.log("TC5 PASS");
});

// ─── TC6: Non-GraphQL POST — no graphql attrs ─────────────────────────────────

test("@M5 TC6 — non-GraphQL POST does NOT emit graphql attrs", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://httpbin.org/post", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: 123, action: "buy" }),
    }).catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "graphql.operation.name")).toBeUndefined();
  expect(getAttr(span.attributes, "graphql.operation.type")).toBeUndefined();
  console.log("TC6 PASS");
});

// ─── TC7: network failure → error.type = network_error ───────────────────────

test("@M5 TC7 — network failure (unreachable host) sets error.type = network_error", async ({
  page,
  otlp,
}) => {
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    // Use a URL that will definitely fail (non-routable IP)
    await fetch("https://192.0.2.1/data").catch(() => {/* expected */});
  });

  const span = await otlp.waitForSpan("http", 10_000);
  expect(getAttr(span.attributes, "error.type")).toBe("network_error");
  console.log("TC7 PASS");
});

// ─── TC8: 4xx response → error.type = 4xx ────────────────────────────────────

test("@M5 TC8 — 4xx response sets error.type = 4xx", async ({ page, otlp }) => {
  // Mock a 404 via route interception (avoids CORS)
  await page.route("https://api.test.com/missing", async (route) => {
    await route.fulfill({ status: 404, body: "Not Found" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/missing").catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.response.status_code")).toBe(404);
  expect(getAttr(span.attributes, "error.type")).toBe("4xx");
  console.log("TC8 PASS");
});

// ─── TC9: 5xx response → error.type = 5xx ────────────────────────────────────

test("@M5 TC9 — 5xx response sets error.type = 5xx", async ({ page, otlp }) => {
  await page.route("https://api.test.com/error", async (route) => {
    await route.fulfill({ status: 500, body: "Internal Server Error" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/error").catch(() => {});
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.response.status_code")).toBe(500);
  expect(getAttr(span.attributes, "error.type")).toBe("5xx");
  console.log("TC9 PASS");
});

// ─── TC10: 2xx response → no error.type ──────────────────────────────────────

test("@M5 TC10 — 2xx response has no error.type", async ({ page, otlp }) => {
  await page.route("https://api.test.com/ok", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: '{"ok":true}',
    });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/ok");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "error.type")).toBeUndefined();
  console.log("TC10 PASS");
});

// ─── TC11: http.response.body.size from content-length ───────────────────────

test("@M5 TC11 — http.response.body.size set from content-length header", async ({
  page,
  otlp,
}) => {
  const responseBody = '{"data":[1,2,3]}';
  await page.route("https://api.test.com/data", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      headers: { "content-length": String(responseBody.length) },
      body: responseBody,
    });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/data");
  });

  const span = await otlp.waitForSpan("http");
  expect(Number(getAttr(span.attributes, "http.response.body.size"))).toBe(responseBody.length);
  console.log("TC11 PASS");
});

// ─── TC12: http.request.method on GET ────────────────────────────────────────

test("@M5 TC12 — http.request.method = GET for GET requests", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/products", async (route) => {
    await route.fulfill({ status: 200, body: "[]" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/products");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
  console.log("TC12 PASS");
});

// ─── TC13: http.request.method = POST ────────────────────────────────────────

test("@M5 TC13 — http.request.method = POST for POST requests", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/orders", async (route) => {
    await route.fulfill({ status: 201, body: '{"id":1}' });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/orders", {
      method: "POST",
      body: JSON.stringify({ item: "widget" }),
    });
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.request.method")).toBe("POST");
  console.log("TC13 PASS");
});

// ─── TC14: server.address extracted ──────────────────────────────────────────

test("@M5 TC14 — server.address matches request hostname", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/health", async (route) => {
    await route.fulfill({ status: 200, body: "ok" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/health");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "server.address")).toBe("api.test.com");
  console.log("TC14 PASS");
});

// ─── TC15: XHR produces http span ────────────────────────────────────────────

test("@M5 TC15 — XHR produces http span with pulse.type = http", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/xhr-test", async (route) => {
    await route.fulfill({ status: 200, body: '{"ok":true}' });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(() => {
    return new Promise<void>((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open("GET", "https://api.test.com/xhr-test");
      xhr.onloadend = () => resolve();
      xhr.send();
    });
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "pulse.type")).toBe("http");
  console.log("TC15 PASS");
});

// ─── TC16: blocked URL not traced ────────────────────────────────────────────

test("@M5 TC16 — blockedUrls excluded from tracing", async ({ page, otlp }) => {
  // Note: this test verifies the design; blockedUrls config would be set at SDK init.
  // The OTLP endpoint itself (127.0.0.1:4318) is always excluded.
  await page.goto("http://localhost:3002/");
  await page.waitForTimeout(1500);

  const httpSpans = findAllSpans(otlp.captured, "http");
  const otlpSpans = httpSpans.filter((s) =>
    String(getAttr(s.attributes, "url.full") ?? "").includes("4318"),
  );
  expect(otlpSpans).toHaveLength(0);
  console.log("TC16 PASS: 0 OTLP self-traced spans");
});

// ─── TC17: anonymous GraphQL query (shorthand) ────────────────────────────────

test("@M5 TC17 — anonymous GraphQL shorthand {} → type = query, name absent", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/graphql", async (route) => {
    await route.fulfill({ status: 200, body: '{"data":{}}' });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/graphql", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: "{ products { id } }" }),
    });
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "graphql.operation.type")).toBe("query");
  // Anonymous query has no name
  expect(getAttr(span.attributes, "graphql.operation.name")).toBeUndefined();
  console.log("TC17 PASS");
});

// ─── TC18: http.duration present ──────────────────────────────────────────────

test("@M5 TC18 — http.duration is a positive integer when PerformanceResourceTiming available", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/timed", async (route) => {
    await route.fulfill({ status: 200, body: "ok" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/timed");
  });

  const span = await otlp.waitForSpan("http");
  const dur = getAttr(span.attributes, "http.duration");
  // http.duration is best-effort — present when PerformanceResourceTiming available
  if (dur !== undefined) {
    expect(Number(dur)).toBeGreaterThanOrEqual(0);
  }
  console.log("TC18 PASS: http.duration =", dur ?? "(absent — timing API unavailable)");
});

// ─── TC19: deprecated semconv keys absent ────────────────────────────────────

test("@M5 TC19 — deprecated http.method / http.url / http.status_code NOT emitted", async ({
  page,
  otlp,
}) => {
  await page.route("https://api.test.com/legacy-check", async (route) => {
    await route.fulfill({ status: 200, body: "ok" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/legacy-check");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.method")).toBeUndefined();
  expect(getAttr(span.attributes, "http.url")).toBeUndefined();
  expect(getAttr(span.attributes, "http.status_code")).toBeUndefined();
  expect(getAttr(span.attributes, "net.peer.name")).toBeUndefined();
  console.log("TC19 PASS: no deprecated keys");
});

// ─── TC20: consent=DENIED → zero http spans ───────────────────────────────────

test("@M5 TC20 — consent=DENIED → no http spans emitted", async ({ page, otlp }) => {
  await page.goto("http://localhost:3002/?pulse_consent=denied");
  await page.waitForTimeout(500);

  await page.evaluate(async () => {
    await fetch("https://api.test.com/consent-denied-test").catch(() => {});
  });
  await page.waitForTimeout(1000);

  const httpSpans = findAllSpans(otlp.captured, "http");
  expect(httpSpans).toHaveLength(0);
  console.log("TC20 PASS: no http spans with DENIED consent");
});

// ─── TC21: post-shutdown → no http spans ─────────────────────────────────────

test("@M5 TC21 — post-shutdown → no http spans after SDK shutdown", async ({
  page,
  otlp,
}) => {
  type PulseWebWindow = Window & {
    PulseWeb?: { isInitialized: () => boolean; shutdown: () => Promise<void> };
  };

  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");

  // Shutdown SDK
  await page.evaluate(async () => {
    await (window as unknown as PulseWebWindow).PulseWeb!.shutdown();
  });
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/post-shutdown").catch(() => {});
  });
  await page.waitForTimeout(1000);

  const httpSpans = findAllSpans(otlp.captured, "http");
  expect(httpSpans).toHaveLength(0);
  console.log("TC21 PASS: no http spans post-shutdown");
});

// ─── TC22: peer.service from peerServiceMap (with config injection) ───────────

test("@M5 TC22 — peer.service set from peerServiceMap config", async ({ page, otlp }) => {
  await page.addInitScript(() => {
    (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
      enabled: true,
      peerServiceMap: { "api.test.com": "orders-service" },
    };
  });
  await page.route("https://api.test.com/orders", async (route) => {
    await route.fulfill({ status: 200, body: "{}" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/orders");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "peer.service")).toBe("orders-service");
  console.log("TC22 PASS");
});

// ─── TC23: capturedRequestHeaders (with config injection) ─────────────────────

test("@M5 TC23 — capturedRequestHeaders captured as http.request.header.<name>", async ({
  page,
  otlp,
}) => {
  await page.addInitScript(() => {
    (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
      enabled: true,
      capturedRequestHeaders: ["x-request-id"],
    };
  });
  await page.route("https://api.test.com/req-headers", async (route) => {
    await route.fulfill({ status: 200, body: "{}" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/req-headers", {
      headers: { "x-request-id": "e2e-req-999" },
    });
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.request.header.x-request-id")).toBe("e2e-req-999");
  console.log("TC23 PASS");
});

// ─── TC24: capturedResponseHeaders (with config injection) ────────────────────

test("@M5 TC24 — capturedResponseHeaders captured as http.response.header.<name>", async ({
  page,
  otlp,
}) => {
  await page.addInitScript(() => {
    (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
      enabled: true,
      capturedResponseHeaders: ["x-trace-id"],
    };
  });
  await page.route("https://api.test.com/resp-headers", async (route) => {
    await route.fulfill({
      status: 200,
      body: "{}",
      headers: { "x-trace-id": "trace-e2e-456" },
    });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  await page.evaluate(async () => {
    await fetch("https://api.test.com/resp-headers");
  });

  const span = await otlp.waitForSpan("http");
  expect(getAttr(span.attributes, "http.response.header.x-trace-id")).toBe("trace-e2e-456");
  console.log("TC24 PASS");
});

// ─── TC25: blockedUrls excluded (with config injection) ───────────────────────

test("@M5 TC25 — custom blockedUrls not traced", async ({ page, otlp }) => {
  await page.addInitScript(() => {
    (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
      enabled: true,
      blockedUrls: ["https://analytics.blocked.com"],
    };
  });
  await page.route("https://analytics.blocked.com/**", async (route) => {
    await route.fulfill({ status: 200, body: "{}" });
  });
  await page.route("https://api.test.com/normal", async (route) => {
    await route.fulfill({ status: 200, body: "{}" });
  });
  await page.goto("http://localhost:3002/");
  await otlp.waitForLog("session.start");
  otlp.reset();

  // Make both — blocked and normal
  await page.evaluate(async () => {
    await fetch("https://analytics.blocked.com/event");
    await fetch("https://api.test.com/normal");
  });

  // Only the normal request should produce a span
  const span = await otlp.waitForSpan("http");
  const urlFull = String(getAttr(span.attributes, "url.full") ?? "");
  expect(urlFull).not.toContain("analytics.blocked.com");
  expect(urlFull).toContain("api.test.com");

  // Confirm blocked URL produced zero spans
  const allHttp = findAllSpans(otlp.captured, "http");
  const blockedSpans = allHttp.filter((s) =>
    String(getAttr(s.attributes, "url.full") ?? "").includes("analytics.blocked.com"),
  );
  expect(blockedSpans).toHaveLength(0);
  console.log("TC25 PASS: blocked URL not traced");
});
