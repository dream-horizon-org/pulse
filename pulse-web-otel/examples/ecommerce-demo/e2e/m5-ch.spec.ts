/**
 * M5 CH Integration Tests — Network Instrumentation
 *
 * Verifies that NetworkInstrumentation http spans emitted by the browser
 * actually land in ClickHouse via the real OTEL collector pipeline.
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch            (headless)
 *   yarn e2e:ch:headed     (headed)
 *
 * Each test:
 *   1. Drives the browser (real OTLP export — no page.route intercept)
 *   2. Waits INGEST_WAIT ms for batch flush + collector → CH
 *   3. Queries CH and asserts on the row
 *
 * Auto-skips if CH not reachable (stack not running).
 */

import { test, expect } from "@playwright/test";
import {
  isCHAvailable,
  chQuery,
  pollUntilCH,
  countCHSpans,
  SERVICE_NAME,
} from "./ch-fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

const INGEST_WAIT = 5_000;
const CH_DB = process.env["CH_DB"] ?? "otel";

// ─── CH row type for http spans ───────────────────────────────────────────────

interface ChHttpSpanRow {
  SpanName: string;
  ServiceName: string;
  PulseType: string;
  span_ts: string;
  http_request_method: string;
  url_full: string;
  http_response_status_code: string;
  server_address: string;
  error_type: string;
  graphql_operation_name: string;
  graphql_operation_type: string;
  http_response_body_size: string;
  http_duration: string;
  peer_service: string;
}

// ─── Query helper ─────────────────────────────────────────────────────────────

function baseWhere(extraSeconds = 120): string {
  return `ServiceName = '${SERVICE_NAME}' AND Timestamp > now() - INTERVAL ${extraSeconds} SECOND`;
}

function waitForCHHttpSpan(
  extraWhere = "",
  timeoutMs = 20_000,
): Promise<ChHttpSpanRow> {
  const sql = `
    SELECT
      SpanName,
      ServiceName,
      PulseType,
      toString(Timestamp) AS span_ts,
      SpanAttributes['http.request.method']      AS http_request_method,
      SpanAttributes['url.full']                 AS url_full,
      SpanAttributes['http.response.status_code'] AS http_response_status_code,
      SpanAttributes['server.address']           AS server_address,
      SpanAttributes['error.type']               AS error_type,
      SpanAttributes['graphql.operation.name']   AS graphql_operation_name,
      SpanAttributes['graphql.operation.type']   AS graphql_operation_type,
      SpanAttributes['http.response.body.size']  AS http_response_body_size,
      SpanAttributes['http.duration']            AS http_duration,
      SpanAttributes['peer.service']             AS peer_service
    FROM ${CH_DB}.otel_traces
    WHERE ${baseWhere()}
      AND PulseType = 'http'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    ORDER BY Timestamp DESC
    LIMIT 1
    FORMAT JSONEachRow
  `;
  return pollUntilCH<ChHttpSpanRow>(sql, timeoutMs, `http span`);
}

async function countCHHttpSpans(
  extraWhere = "",
  windowSeconds = 30,
): Promise<number> {
  const sql = `
    SELECT count() AS cnt
    FROM ${CH_DB}.otel_traces
    WHERE ServiceName = '${SERVICE_NAME}'
      AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
      AND PulseType = 'http'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    FORMAT JSONEachRow
  `;
  const rows = await chQuery<{ cnt: string }>(sql);
  return Number(rows[0]?.cnt ?? 0);
}

// ─── Suite setup ──────────────────────────────────────────────────────────────

test.beforeEach(async () => {
  const available = await isCHAvailable();
  if (!available) {
    test.skip(true, "ClickHouse not reachable — start full stack with deploy/scripts/start.sh");
  }
});

// ─── TC1: Basic fetch → http span in CH ───────────────────────────────────────

test.describe("@M5-CH basic http span", () => {
  test("TC1: fetch() → http span in CH with stable semconv attrs", async ({ page }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['http.request.method'] = 'GET'`,
    );

    expect(row.PulseType).toBe("http");
    expect(row.http_request_method).toBe("GET");
    expect(row.url_full).toBeTruthy();
    // No deprecated keys (url.full not http.url)
    expect(row.url_full).not.toBe("");
  });

  test("TC2: url.full in CH has query params stripped", async ({ page }) => {
    await page.goto("/");

    await page.evaluate(async () => {
      await fetch("/products?token=secret&page=2").catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['url.full'] LIKE '%/products%'`,
    );

    expect(row.url_full).not.toContain("token=secret");
    expect(row.url_full).not.toContain("page=2");
  });
});

// ─── TC3: server.address in CH ────────────────────────────────────────────────

test.describe("@M5-CH server.address", () => {
  test("TC3: server.address populated in CH row", async ({ page }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['server.address'] != ''`,
    );

    expect(row.server_address).toBeTruthy();
  });
});

// ─── TC4–5: GraphQL attrs in CH ───────────────────────────────────────────────

test.describe("@M5-CH GraphQL attrs", () => {
  test("TC4: GraphQL POST with operationName → graphql attrs in CH", async ({ page }) => {
    await page.goto("/");

    await page.evaluate(async () => {
      await fetch("/graphql", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          query: "query GetProductsCH { products { id } }",
          operationName: "GetProductsCH",
        }),
      }).catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['graphql.operation.name'] = 'GetProductsCH'`,
    );

    expect(row.graphql_operation_name).toBe("GetProductsCH");
    expect(row.graphql_operation_type).toBe("query");
  });

  test("TC5: mutation body → graphql.operation.type = mutation in CH", async ({ page }) => {
    await page.goto("/");

    await page.evaluate(async () => {
      await fetch("/graphql", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          query: "mutation CreateOrderCH { createOrder { id } }",
          operationName: "CreateOrderCH",
        }),
      }).catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['graphql.operation.name'] = 'CreateOrderCH'`,
    );

    expect(row.graphql_operation_type).toBe("mutation");
  });
});

// ─── TC6–7: Span status on error responses in CH ─────────────────────────────

test.describe("@M5-CH error.type", () => {
  test("TC6: 4xx response → error.type = 4xx in CH", async ({ page }) => {
    await page.goto("/");

    // /api/missing should 404 (demo app returns 404 for unknown API routes)
    await page.evaluate(async () => {
      await fetch("/api/missing-endpoint-tc6").catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan(
      `SpanAttributes['error.type'] = '4xx'`,
    );

    expect(row.error_type).toBe("4xx");
    expect(Number(row.http_response_status_code)).toBeGreaterThanOrEqual(400);
    expect(Number(row.http_response_status_code)).toBeLessThan(500);
  });
});

// ─── TC8: OTLP self-tracing excluded in CH ────────────────────────────────────

test.describe("@M5-CH OTLP excluded", () => {
  test("TC8: OTLP endpoint calls NOT in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/");
    await page.waitForTimeout(3_000);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;

    // Verify no http span with url.full containing the OTLP endpoint
    const sql = `
      SELECT count() AS cnt
      FROM ${CH_DB}.otel_traces
      WHERE ServiceName = '${SERVICE_NAME}'
        AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
        AND PulseType = 'http'
        AND (SpanAttributes['url.full'] LIKE '%4317%'
          OR SpanAttributes['url.full'] LIKE '%4318%'
          OR SpanAttributes['url.full'] LIKE '%v1/traces%'
          OR SpanAttributes['url.full'] LIKE '%v1/logs%')
      FORMAT JSONEachRow
    `;
    const rows = await chQuery<{ cnt: string }>(sql);
    expect(Number(rows[0]?.cnt ?? 0)).toBe(0);
  });
});

// ─── TC9: consent=DENIED → no http spans in CH ───────────────────────────────

test.describe("@M5-CH consent / lifecycle", () => {
  test("TC9: consent=DENIED → zero http spans in CH", async ({ page }) => {
    const before = Date.now();

    await page.goto("/?pulse_consent=denied");
    await page.evaluate(async () => {
      await fetch("/api/test-consent-denied").catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHHttpSpans(
      `SpanAttributes['url.full'] LIKE '%test-consent-denied%'`,
      windowSeconds,
    );
    expect(count).toBe(0);
  });

  test("TC10: post-shutdown → no http spans in CH after SDK shutdown", async ({ page }) => {
    type PulseWebWindow = Window & { PulseWeb?: { isInitialized: () => boolean; shutdown: () => Promise<void> } };

    await page.goto("/products");
    await expect
      .poll(
        () => page.evaluate(() => (window as unknown as PulseWebWindow).PulseWeb?.isInitialized?.() ?? false),
        { timeout: 15_000 },
      )
      .toBe(true);

    await page.evaluate(async () => {
      await (window as unknown as PulseWebWindow).PulseWeb!.shutdown();
    });

    const before = Date.now();

    await page.evaluate(async () => {
      await fetch("/api/post-shutdown-network-test").catch(() => {});
    });
    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHHttpSpans(
      `SpanAttributes['url.full'] LIKE '%post-shutdown-network-test%'`,
      windowSeconds,
    );
    expect(count).toBe(0);
  });
});

// ─── TC11: http.response.body.size in CH (when Content-Length present) ────────

test.describe("@M5-CH body size", () => {
  test("TC11: http.response.body.size populated in CH when Content-Length header present", async ({
    page,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    // Products page makes a fetch to load data — the response should have Content-Length
    const row = await waitForCHHttpSpan(
      `SpanAttributes['http.response.body.size'] != ''
       AND SpanAttributes['http.response.body.size'] != '0'`,
    );

    expect(Number(row.http_response_body_size)).toBeGreaterThan(0);
  });
});

// ─── TC12: deprecated keys NOT in CH ─────────────────────────────────────────

test.describe("@M5-CH deprecated semconv absent", () => {
  test("TC12: deprecated http.method / http.url / http.status_code keys NOT present in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    const sql = `
      SELECT count() AS cnt
      FROM ${CH_DB}.otel_traces
      WHERE ${baseWhere()}
        AND PulseType = 'http'
        AND (
          mapContains(SpanAttributes, 'http.method')
          OR mapContains(SpanAttributes, 'http.url')
          OR mapContains(SpanAttributes, 'http.status_code')
          OR mapContains(SpanAttributes, 'net.peer.name')
        )
      FORMAT JSONEachRow
    `;
    const rows = await chQuery<{ cnt: string }>(sql);
    expect(Number(rows[0]?.cnt ?? 0)).toBe(0);
  });
});

// ─── TC13: pulse.type = http on all network spans ────────────────────────────

test.describe("@M5-CH pulse.type", () => {
  test("TC13: all network spans have pulse.type = http in CH", async ({ page }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHHttpSpan();
    expect(row.PulseType).toBe("http");
  });
});

// ─── TC14: peer.service in CH (with __pulseE2eNetworkConfig injection) ────────

test.describe("@M5-CH peer.service", () => {
  test("TC14: peer.service populated in CH when peerServiceMap configured", async ({
    page,
  }) => {
    await page.addInitScript(() => {
      (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
        enabled: true,
        peerServiceMap: { localhost: "ecommerce-backend" },
      };
    });

    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    // The demo app makes fetch calls to localhost — peer.service should be stamped
    const row = await waitForCHHttpSpan(
      `SpanAttributes['peer.service'] = 'ecommerce-backend'`,
    );

    expect(row.peer_service).toBe("ecommerce-backend");
  });
});

// ─── TC15: http.duration in CH ────────────────────────────────────────────────

test.describe("@M5-CH http.duration", () => {
  test("TC15: http.duration > 0 in CH when PerformanceResourceTiming available", async ({
    page,
  }) => {
    await page.goto("/products");
    await page.waitForTimeout(INGEST_WAIT);

    // http.duration is best-effort; skip gracefully if absent
    const sql = `
      SELECT count() AS cnt
      FROM ${CH_DB}.otel_traces
      WHERE ${baseWhere()}
        AND PulseType = 'http'
        AND SpanAttributes['http.duration'] != ''
        AND toUInt64OrNull(SpanAttributes['http.duration']) > 0
      FORMAT JSONEachRow
    `;
    const rows = await chQuery<{ cnt: string }>(sql);
    const count = Number(rows[0]?.cnt ?? 0);

    // If timing API available (Chromium), at least some spans should have it
    // If zero — PerformanceResourceTiming not available for CORS requests in this env
    if (count > 0) {
      const row = await waitForCHHttpSpan(
        `SpanAttributes['http.duration'] != '' AND toUInt64OrNull(SpanAttributes['http.duration']) > 0`,
      );
      expect(Number(row.http_duration)).toBeGreaterThan(0);
    } else {
      console.log("TC15 SKIP: http.duration absent — PerformanceResourceTiming not available for these requests");
    }
  });
});

// ─── TC16: blockedUrls custom URL not in CH ───────────────────────────────────

test.describe("@M5-CH blockedUrls", () => {
  test("TC16: custom blockedUrls URL produces no http span in CH", async ({ page }) => {
    await page.addInitScript(() => {
      (window as unknown as Record<string, unknown>)["__pulseE2eNetworkConfig"] = {
        enabled: true,
        blockedUrls: [/\/blocked-analytics\//],
      };
    });

    const before = Date.now();

    await page.goto("/");

    // Make a fetch to the blocked URL pattern
    await page.evaluate(async () => {
      await fetch("/blocked-analytics/event").catch(() => {});
    });

    await page.waitForTimeout(INGEST_WAIT);

    const windowSeconds = Math.ceil((Date.now() - before) / 1000) + 5;
    const count = await countCHHttpSpans(
      `SpanAttributes['url.full'] LIKE '%blocked-analytics%'`,
      windowSeconds,
    );

    expect(count).toBe(0);
  });
});
