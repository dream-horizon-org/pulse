/**
 * M1 CH Integration Tests — Foundation: Installation ID, Session ID,
 * Resource Attributes, and Session Signals in ClickHouse.
 *
 * Verifies that the core SDK signals (session.start, session.end) emitted by
 * the browser actually land in ClickHouse via the real OTEL collector pipeline,
 * and that all required attributes (installation.id, session.id, platform,
 * browser.name, os.name, device.type, screen.name, url.path) are present.
 *
 * REQUIRES full stack running:
 *   cd deploy && ./scripts/start.sh
 *
 * Run:
 *   yarn e2e:ch            (headless)
 *   yarn e2e:ch:headed     (headed for debugging)
 *
 * Each test:
 *   1. Drives the browser (real OTLP export — no page.route() intercept)
 *   2. Waits INGEST_WAIT ms for batch flush + collector → CH ingest
 *   3. Queries CH and asserts on the row
 *
 * Auto-skips entire suite if CH is not reachable (stack not running).
 */

import { test, expect } from "@playwright/test";
import {
  isCHAvailable,
  chQuery,
  pollUntilCH,
  waitForCHLog,
  countCHLogs,
  SERVICE_NAME,
} from "./ch-fixture";

// ─── Constants ────────────────────────────────────────────────────────────────

/** 1s batch delay + ~4s collector→CH ingest latency. pollUntilCH retries anyway. */
const INGEST_WAIT = 5_000;
const CH_DB = process.env["CH_DB"] ?? "otel";

// ─── Helpers ──────────────────────────────────────────────────────────────────

type PulseWebWindow = Window & {
  PulseWeb?: {
    isInitialized: () => boolean;
    shutdown: () => Promise<void>;
  };
};

async function waitForSdkInit(page: import("@playwright/test").Page): Promise<void> {
  await expect
    .poll(
      () =>
        page.evaluate(() =>
          (window as unknown as PulseWebWindow).PulseWeb?.isInitialized?.() ?? false,
        ),
      { timeout: 15_000 },
    )
    .toBe(true);
}

/** Row shape from otel_logs with resource attributes */
interface ChSessionLogRow {
  log_ts: string;
  PulseType: string;
  Body: string;
  session_id: string;
  installation_id: string;
  screen_name: string;
  platform: string;
  browser_name: string;
  os_name: string;
  device_type: string;
  url_path: string;
  project_id: string;
}

function waitForCHSessionLog(
  pulseType: "session.start" | "session.end",
  extraWhere = "",
  timeoutMs = 20_000,
): Promise<ChSessionLogRow> {
  const sql = `
    SELECT
      toString(Timestamp)                         AS log_ts,
      PulseType,
      Body,
      LogAttributes['session.id']                 AS session_id,
      LogAttributes['installation.id']            AS installation_id,
      LogAttributes['screen.name']                AS screen_name,
      LogAttributes['url.path']                   AS url_path,
      ResourceAttributes['platform']              AS platform,
      ResourceAttributes['browser.name']          AS browser_name,
      ResourceAttributes['os.name']               AS os_name,
      ResourceAttributes['device.type']           AS device_type,
      ResourceAttributes['project.id']            AS project_id
    FROM ${CH_DB}.otel_logs
    WHERE ServiceName = '${SERVICE_NAME}'
      AND Timestamp > now() - INTERVAL 120 SECOND
      AND PulseType = '${pulseType}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    ORDER BY Timestamp DESC
    LIMIT 1
    FORMAT JSONEachRow
  `;
  return pollUntilCH<ChSessionLogRow>(sql, timeoutMs, `log(${pulseType})`);
}

// ─── Suite setup ──────────────────────────────────────────────────────────────

test.beforeEach(async () => {
  const available = await isCHAvailable();
  if (!available) {
    test.skip(true, "ClickHouse not reachable — start full stack with deploy/scripts/start.sh");
  }
});

// ─── TC 1.1–1.2 — Installation ID in ClickHouse ──────────────────────────────

test.describe("@M1-CH TC 1.x — installation.id in ClickHouse", () => {
  test("TC 1.1: installation.id is present and non-empty on session.start log in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.installation_id).toBeTruthy();
    expect(row.installation_id.length).toBeGreaterThan(0);
    console.log("TC 1.1 PASS — installation_id:", row.installation_id);
  });

  test("TC 1.2: installation.id in CH matches UUID v4 format", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.installation_id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    console.log("TC 1.2 PASS — installation_id UUID format verified");
  });

  test("TC 1.3: same installation.id on reload (persisted in localStorage)", async ({
    page,
    context,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    // Capture the installation ID from the first session.start
    const row1 = await waitForCHSessionLog("session.start");
    const installId1 = row1.installation_id;
    expect(installId1).toBeTruthy();

    // Reload: same tab = same installation ID
    await page.reload();
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    // Query fresh session.start from the reload
    const row2 = await waitForCHSessionLog("session.start");
    // Both rows will be in the last 120s window; both should have same installation.id
    expect(row2.installation_id).toBe(installId1);
    console.log("TC 1.3 PASS — installation.id stable across reload:", installId1);

    void context; // not used but required by type
  });
});

// ─── TC 1.10–1.12 — Session ID in ClickHouse ─────────────────────────────────

test.describe("@M1-CH TC 1.x — session.id in ClickHouse", () => {
  test("TC 1.10: session.id present and non-empty on session.start log in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.session_id).toBeTruthy();
    expect(row.session_id.length).toBeGreaterThan(0);
    console.log("TC 1.10 PASS — session_id:", row.session_id);
  });

  test("TC 1.11: session.id in CH matches UUID v4 format", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.session_id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    console.log("TC 1.11 PASS — session_id UUID format verified");
  });
});

// ─── TC 2.1–2.25 — Resource / Global Attributes in ClickHouse ────────────────

test.describe("@M1-CH TC 2.x — resource attributes in ClickHouse", () => {
  test("TC 2.1: platform=web on session.start log in CH", async ({ page }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.platform).toBe("web");
    console.log("TC 2.1 PASS — platform=web");
  });

  test("TC 2.2: browser.name is non-empty on session.start in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.browser_name).toBeTruthy();
    expect(row.browser_name.length).toBeGreaterThan(0);
    console.log("TC 2.2 PASS — browser.name:", row.browser_name);
  });

  test("TC 2.3: os.name is non-empty on session.start in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.os_name).toBeTruthy();
    expect(row.os_name.length).toBeGreaterThan(0);
    console.log("TC 2.3 PASS — os.name:", row.os_name);
  });

  test("TC 2.4: device.type is 'desktop' when running in headless Chromium", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(["desktop", "mobile", "tablet"]).toContain(row.device_type);
    // Headless chromium with desktop viewport → desktop
    expect(row.device_type).toBe("desktop");
    console.log("TC 2.4 PASS — device.type:", row.device_type);
  });

  test("TC 2.5: project.id derived from API key is non-empty in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    expect(row.project_id).toBeTruthy();
    expect(row.project_id.length).toBeGreaterThan(0);
    console.log("TC 2.5 PASS — project.id:", row.project_id);
  });

  test("TC 2.6: screen.name present on session.start log in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog("session.start");

    // screen.name is derived from URL path by heuristic: /products → /products
    expect(row.screen_name).toBeTruthy();
    console.log("TC 2.6 PASS — screen.name:", row.screen_name);
  });

  test("TC 2.7: url.path present on session.start log in CH", async ({
    page,
  }) => {
    await page.goto("/products");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHSessionLog(
      "session.start",
      `LogAttributes['url.path'] = '/products'`,
    );

    expect(row.url_path).toBe("/products");
    console.log("TC 2.7 PASS — url.path:", row.url_path);
  });
});

// ─── TC 3.1–3.5 — Session Signals in ClickHouse ──────────────────────────────

test.describe("@M1-CH TC 3.x — session signals in ClickHouse", () => {
  test("TC 3.1: session.start arrives in otel_logs with Body='session.start'", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog("session.start");

    expect(row.PulseType).toBe("session.start");
    expect(row.Body).toBe("session.start");
    expect(row.session_id).toBeTruthy();
    expect(row.installation_id).toBeTruthy();
    console.log("TC 3.1 PASS — session.start in CH");
  });

  test("TC 3.2: session.start does NOT fire again on SPA navigation (only 1 per page load)", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);

    // SPA navigate to products
    await page.click("a[href='/products']");
    await page.waitForTimeout(INGEST_WAIT);

    // Count session.start logs in the last 30 seconds for this navigation
    // SPA nav must NOT emit a second session.start
    const sessionStartCountBefore = await countCHLogs("session.start", "", 60);

    await page.click("a[href='/cart']");
    await page.waitForTimeout(INGEST_WAIT);

    const sessionStartCountAfter = await countCHLogs("session.start", "", 60);

    // After SPA navigations, count should not have grown further
    // (only the initial load produced a session.start)
    expect(sessionStartCountAfter).toBe(sessionStartCountBefore);
    console.log("TC 3.2 PASS — SPA nav did not emit extra session.start");
  });

  test("TC 3.3: session.end arrives in otel_logs after page unload (pagehide)", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);

    // Dispatch pagehide with persisted=false to trigger session.end
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog("session.end");

    expect(row.PulseType).toBe("session.end");
    expect(row.Body).toBe("session.end");
    expect(row.session_id).toBeTruthy();
    console.log("TC 3.3 PASS — session.end in CH");
  });

  test("TC 3.4: BFCache pagehide (persisted=true) does NOT produce session.end in CH", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);

    // Record count of session.end logs before BFCache pagehide
    const countBefore = await countCHLogs("session.end", "", 30);

    // BFCache pagehide: persisted=true → must NOT emit session.end
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
      );
    });

    // Wait for any signal to flush (if incorrectly emitted)
    await page.waitForTimeout(INGEST_WAIT);

    const countAfter = await countCHLogs("session.end", "", 30);

    // BFCache pagehide must not have produced new session.end rows
    expect(countAfter).toBe(countBefore);
    console.log("TC 3.4 PASS — BFCache pagehide did not emit session.end");
  });

  test("TC 3.5: session.end log in CH carries non-empty session.id", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog("session.end");

    expect(row.session_id).toBeTruthy();
    expect(row.session_id.length).toBeGreaterThan(0);
    console.log("TC 3.5 PASS — session.end carries session_id:", row.session_id);
  });

  test("TC 3.6: session.start and session.end carry the same session.id", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const startRow = await waitForCHSessionLog("session.start");

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    await page.waitForTimeout(INGEST_WAIT);

    const endRow = await waitForCHLog(
      "session.end",
      `LogAttributes['session.id'] = '${startRow.session_id}'`,
    );

    expect(endRow.session_id).toBe(startRow.session_id);
    console.log("TC 3.6 PASS — start/end share session_id:", startRow.session_id);
  });
});

// ─── SDK init signals in ClickHouse ──────────────────────────────────────────

test.describe("@M1-CH SDK init signals", () => {
  test("rum.sdk.init.started log arrives in CH after SDK start()", async ({
    page,
  }) => {
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const sql = `
      SELECT
        toString(Timestamp) AS log_ts,
        Body,
        LogAttributes['span.exporter'] AS span_exporter
      FROM ${CH_DB}.otel_logs
      WHERE ServiceName = '${SERVICE_NAME}'
        AND Timestamp > now() - INTERVAL 120 SECOND
        AND Body = 'rum.sdk.init.started'
      ORDER BY Timestamp DESC
      LIMIT 1
      FORMAT JSONEachRow
    `;

    const rows = await pollUntilCH<{ log_ts: string; Body: string; span_exporter: string }>(
      sql,
      20_000,
      "rum.sdk.init.started",
    );

    expect(rows.Body).toBe("rum.sdk.init.started");
    console.log("SDK init PASS — rum.sdk.init.started in CH");
  });

  test("X-API-KEY accepted by collector: signals reach CH", async ({
    page,
  }) => {
    // If session.start arrives in CH, the X-API-KEY header was valid.
    await page.goto("/");
    await waitForSdkInit(page);
    await page.waitForTimeout(INGEST_WAIT);

    const row = await waitForCHLog("session.start");

    // Presence of session.start in CH proves the OTLP request with X-API-KEY was accepted
    expect(row.PulseType).toBe("session.start");
    console.log("API key PASS — signals reached CH:", row.session_id);
  });
});
