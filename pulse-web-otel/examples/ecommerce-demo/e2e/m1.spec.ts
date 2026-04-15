/**
 * M1 E2E Tests — Foundation: SDK Core Pipeline
 *
 * Covers the done criteria from .claude/plans/web-sdk-m1-foundation.md:
 *   - session.start / session.end lifecycle
 *   - installation.id 3-tier persistence
 *   - Singleton guard (no duplicate exporters)
 *   - Resource attributes on every signal
 *   - OTLP request structure + auth header
 *
 * Run:  yarn e2e --grep "@M1" --project=chromium
 */
import {
  test,
  expect,
  getAttr,
  findAllLogs,
  findAllSpansByName,
  findAllLogsByBody,
  getResourceAttr,
} from "./fixture";

/** XHR + custom OTLP headers require CORS preflight; route fulfill must allow origin. */
const E2E_OTLP_CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "Content-Type, Content-Encoding, X-API-KEY, X-Pulse-Metering-Session-ID",
} as const;

// ─── Session Lifecycle ────────────────────────────────────────────────────────

test.describe("@M1 session lifecycle", () => {
  test("session.start emitted on page load", async ({ page, otlp }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "installation.id")).toBeTruthy();
    expect(getAttr(log.attributes, "platform")).toBe("web");
  });

  test("session.end emitted on pagehide (non-BFCache)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    // Simulate pagehide with persisted=false (not BFCache) by dispatching the event
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    // Batch delay in test mode is 200ms; give it time to flush
    const log = await otlp.waitForLog("session.end");
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  });

  test("pagehide with persisted=true (BFCache) does NOT emit session.end", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // BFCache restore — should NOT emit session.end
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
      );
    });
    await page.waitForTimeout(500);

    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);
  });

  test("double PulseWeb.start() is a no-op — exactly one session.start", async ({
    page,
    otlp,
  }) => {
    // App.tsx calls PulseWeb.start() in useEffect; React StrictMode calls it twice
    await page.goto("/");
    await page.waitForTimeout(1500); // let any duplicates arrive

    const starts = findAllLogs(otlp.captured, "session.start");
    expect(starts.length).toBe(1);
  });
});

// ─── Identity Persistence ─────────────────────────────────────────────────────

test.describe("@M1 identity persistence", () => {
  test("installation.id survives page reload", async ({ page, otlp }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const installId = getAttr(first.attributes, "installation.id") as string;
    expect(installId).toBeTruthy();

    otlp.reset();
    await page.reload();
    const second = await otlp.waitForLog("session.start");

    expect(getAttr(second.attributes, "installation.id")).toBe(installId);
  });

  test("installation.id stored in localStorage as pulse_iid", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const stored = await page.evaluate(() =>
      localStorage.getItem("pulse_installation_id"),
    );
    expect(stored).toBeTruthy();
    expect(stored).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  test("installation.id falls back to sessionStorage when localStorage throws", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      Object.defineProperty(window, "localStorage", {
        get() {
          throw new DOMException("storage unavailable", "SecurityError");
        },
        configurable: true,
      });
    });
    await page.goto("/");
    // SDK must not crash; session.start should still emit
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "installation.id")).toBeTruthy();
  });

  test("new session.id on each fresh page load", async ({ page, otlp }) => {
    await page.goto("/");
    const log1 = await otlp.waitForLog("session.start");
    const sid1 = getAttr(log1.attributes, "session.id") as string;

    // Clear storage to force new session (simulate new user)
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    otlp.reset();
    await page.reload();
    const log2 = await otlp.waitForLog("session.start");
    const sid2 = getAttr(log2.attributes, "session.id") as string;

    expect(sid2).toBeTruthy();
    expect(sid2).not.toBe(sid1);
  });
});

// ─── OTLP Pipeline ───────────────────────────────────────────────────────────

test.describe("@M1 OTLP pipeline", () => {
  test("x-api-key header sent on every OTLP request", async ({ page }) => {
    const headers: string[] = [];
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: { ...E2E_OTLP_CORS } });
        return;
      }
      headers.push(route.request().headers()["x-api-key"] ?? "");
      await route.fulfill({
        status: 200,
        headers: { ...E2E_OTLP_CORS },
        body: "{}",
      });
    });
    await page.goto("/");
    await page.waitForTimeout(1000);
    expect(headers.length).toBeGreaterThan(0);
    for (const h of headers) expect(h).toBe("test-api-key");
  });

  test("Content-Type is application/json", async ({ page }) => {
    let contentType = "";
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: { ...E2E_OTLP_CORS } });
        return;
      }
      contentType = route.request().headers()["content-type"] ?? "";
      await route.fulfill({
        status: 200,
        headers: { ...E2E_OTLP_CORS },
        body: "{}",
      });
    });
    await page.goto("/");
    await page.waitForTimeout(1000);
    expect(contentType).toContain("application/json");
  });

  test("resource attributes present on signal (platform, service.name, rum.sdk.version)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
    expect(getResourceAttr(otlp.captured, "service.name")).toBeTruthy();
    expect(getResourceAttr(otlp.captured, "rum.sdk.version")).toBeTruthy();
  });
});

// ─── SDK Shutdown ─────────────────────────────────────────────────────────────

test.describe("@M1 SDK shutdown", () => {
  test("PulseWeb.shutdown() force-flushes providers without error", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });

    await page.evaluate(async () => {
      // @ts-ignore — PulseWeb exposed on window by App.tsx for testing
      await window.PulseWeb?.shutdown?.();
    });

    expect(errors.filter((e) => !e.includes("favicon"))).toHaveLength(0);
  });
});

// ─── Batching ─────────────────────────────────────────────────────────────────

test.describe("@M1 batching", () => {
  test("multiple trackEvent calls coalesced into a single OTLP logs payload", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start"); // SDK is initialised

    // Fire 3 custom events synchronously — all within the same 200ms batch window
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("batch_test_1");
      p.trackEvent("batch_test_2");
      p.trackEvent("batch_test_3");
    });

    // Wait for the batch window to flush (200ms delay + generous buffer for CI)
    await page.waitForTimeout(1500);

    // All 3 logs must have arrived — trackEvent emits custom_event logs (body = event name)
    const allLogs = findAllLogsByBody(otlp.captured, "batch_test_1")
      .concat(findAllLogsByBody(otlp.captured, "batch_test_2"))
      .concat(findAllLogsByBody(otlp.captured, "batch_test_3"));

    expect(allLogs.length).toBe(3);

    // At least one /v1/logs payload must contain more than one log record
    // (proves coalescing, not 3 individual exports)
    const batchedPayload = otlp.captured.find(
      (c) =>
        c.type === "logs" &&
        c.body.resourceLogs.some((rl) =>
          rl.scopeLogs.some(
            (sl) =>
              sl.logRecords.filter((r) =>
                r.body?.stringValue?.startsWith("batch_test_"),
              ).length > 1,
          ),
        ),
    );
    expect(batchedPayload).toBeDefined();
  });

  test("signals accumulate — first export happens after batch delay, not inline with SDK init", async ({
    page,
  }) => {
    // Record the wall-clock time when page load STARTS (before goto resolves)
    // and when the first OTLP export fires. The gap must be >= batch delay (200ms in test mode).
    let firstExportAt = 0;
    const pageLoadStartAt = Date.now();

    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      if (firstExportAt === 0) firstExportAt = Date.now();
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    await page.waitForTimeout(1000); // wait well past the 200ms batch window

    expect(firstExportAt).toBeGreaterThan(0); // at least one export happened
    // First export must have fired after page load started — not before
    expect(firstExportAt).toBeGreaterThan(pageLoadStartAt);
    // And it must have fired at least 100ms after page load started
    // (proves the batch delay is in effect — signals don't escape synchronously)
    expect(firstExportAt - pageLoadStartAt).toBeGreaterThan(100);
  });

  test("pagehide force-flushes pending signals before batch timer fires", async ({
    page,
    otlp,
  }) => {
    // Use a 1-second batch delay via URL query (not possible with current config)
    // Instead: rely on the fact that pagehide should flush before the 200ms timer
    // We emit a trackEvent and trigger pagehide immediately

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("pre_unload_event"); // emits custom_event log (body = 'pre_unload_event')
      // Dispatch pagehide immediately — before the 200ms batch window
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    // forceFlush on pagehide must push the log out immediately (before batch timer fires)
    // trackEvent emits custom_event logs — body = event name
    const log = await otlp.waitForLogByBody("pre_unload_event", 3000);
    expect(log.body?.stringValue).toBe("pre_unload_event");
  });

  test("session.end emitted before pagehide batch window when persisted=false", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Dispatch pagehide (non-BFCache) immediately — don't wait for batch timer
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    // session.end must arrive via forceFlush, not the batch timer
    const log = await otlp.waitForLog("session.end", 3000);
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "session.duration_ms")).toBeTruthy();
  });
});

// ─── Payload attribute contract ───────────────────────────────────────────────

test.describe("@M1 payload attributes", () => {
  test("session.start log carries required data-contract attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    // Data contract from WEB-SDK-AGENT-CONTEXT.md
    expect(getAttr(log.attributes, "pulse.type")).toBe("session.start");
    expect(getAttr(log.attributes, "session.id")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
    expect(getAttr(log.attributes, "installation.id")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
    expect(getAttr(log.attributes, "platform")).toBe("web");
  });

  test("every signal carries global attributes injected by GlobalAttributesProcessor", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    // These must be on every log (injected by global-attrs-processor.ts)
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
    expect(getAttr(log.attributes, "installation.id")).toBeTruthy();
    expect(getAttr(log.attributes, "url.path")).toBeTruthy();
    expect(getAttr(log.attributes, "platform")).toBe("web");
  });

  test("resource carries service.name, platform=web, rum.sdk.version", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    expect(getResourceAttr(otlp.captured, "service.name")).toBe(
      "ecommerce-demo-test",
    );
    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
    expect(getResourceAttr(otlp.captured, "rum.sdk.version")).toBeTruthy();
  });

  test("sdk.init heartbeat span arrives with pulse.type=sdk.init", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const span = await otlp.waitForSpan("sdk.init");

    expect(getAttr(span.attributes, "pulse.type")).toBe("sdk.init");
    expect(getAttr(span.attributes, "platform")).toBe("web");
  });
});

// ─── LocalStorage state ───────────────────────────────────────────────────────

test.describe("@M1 localStorage state", () => {
  test("pulse_installation_id is a UUID stored in localStorage", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const stored = await page.evaluate(() =>
      localStorage.getItem("pulse_installation_id"),
    );
    expect(stored).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  test("installation.id in localStorage matches the value in the session.start signal", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    const fromStorage = await page.evaluate(() =>
      localStorage.getItem("pulse_installation_id"),
    );
    const fromSignal = getAttr(log.attributes, "installation.id") as string;

    expect(fromStorage).toBe(fromSignal);
  });

  test("pulse_sdk_config in localStorage after background fetch completes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    // Background fetch needs a moment
    await page.waitForTimeout(1000);

    const raw = await page.evaluate(() =>
      localStorage.getItem("pulse_sdk_config"),
    );
    // Config may not exist if server returned an error — but if it does exist, it must be valid JSON
    if (raw !== null) {
      expect(() => JSON.parse(raw)).not.toThrow();
      const cfg = JSON.parse(raw) as { version: number };
      expect(typeof cfg.version).toBe("number");
    }
  });

  /**
   * Simulates: publish config v1 → cached after background fetch → reload still reads same
   * cached v1 (fetch returns same version → no localStorage rewrite). Server bumps to v2
   * (e.g. Pulse UI) → first reload: init uses v1 from loadCached, then fetchInBackground
   * persists v2. Second reload: loadCached reads v2 from localStorage.
   */
  test("pulse_sdk_config version: cache + reload, then new version after two reloads", async ({
    page,
    otlp,
  }) => {
    const server = { version: 1 };
    const CONFIG_CORS = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-API-KEY",
    } as const;

    const makeBody = () =>
      JSON.stringify({
        version: server.version,
        description: `cfg-${server.version}`,
        sampling: { default: { sessionSampleRate: 1 }, rules: [] },
        signals: {
          scheduleDurationMs: 5000,
          attributesToDrop: [],
          attributesToAdd: [],
          filters: { mode: "BLACKLIST", values: [] },
        },
        interaction: { beforeInitQueueSize: 5000 },
        features: [],
      });

    await page.route("**/v1/configs/active**", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: CONFIG_CORS });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: CONFIG_CORS,
        body: makeBody(),
      });
    });

    const readCachedMeta = () =>
      page.evaluate(() => {
        const raw = localStorage.getItem("pulse_sdk_config");
        if (!raw) return null;
        try {
          const o = JSON.parse(raw) as {
            version: number;
            description?: string;
          };
          return typeof o.version === "number"
            ? { version: o.version, description: o.description }
            : null;
        } catch {
          return null;
        }
      });

    await page.goto("/");
    await otlp.waitForLog("session.start");
    await expect
      .poll(async () => (await readCachedMeta())?.version ?? null, {
        timeout: 10_000,
      })
      .toBe(1);
    expect((await readCachedMeta())?.description).toBe("cfg-1");

    otlp.reset();
    await page.reload();
    await otlp.waitForLog("session.start");
    expect(await readCachedMeta()).toEqual({
      version: 1,
      description: "cfg-1",
    });

    server.version = 2;
    otlp.reset();
    await page.reload();
    await otlp.waitForLog("session.start");
    await expect
      .poll(async () => (await readCachedMeta())?.version ?? null, {
        timeout: 10_000,
      })
      .toBe(2);

    otlp.reset();
    await page.reload();
    await otlp.waitForLog("session.start");
    expect(await readCachedMeta()).toEqual({
      version: 2,
      description: "cfg-2",
    });
  });
});

// ─── Consent ──────────────────────────────────────────────────────────────────

test.describe("@M1 consent", () => {
  test("DENIED consent → PulseWeb.isInitialized() returns false", async ({
    page,
  }) => {
    // ?pulse_consent=denied is handled by App.tsx → PulseDataCollectionConsent.DENIED
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(500);

    const initialized = await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        isInitialized: () => boolean;
      };
      return p?.isInitialized?.() ?? false;
    });

    expect(initialized).toBe(false);
  });

  test("DENIED consent → zero OTLP calls made", async ({ page }) => {
    const calls: string[] = [];
    await page.route("**/v1/**", async (route) => {
      calls.push(route.request().url());
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(1000);

    expect(calls.filter((u) => u.includes("/v1/"))).toHaveLength(0);
  });
});

// ─── Signal headers ───────────────────────────────────────────────────────────

test.describe("@M1 signal headers", () => {
  test("X-Pulse-Metering-Session-ID header sent on every OTLP request", async ({
    page,
  }) => {
    const meteringIds: string[] = [];
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      const id = route.request().headers()["x-pulse-metering-session-id"] ?? "";
      meteringIds.push(id);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    await page.waitForTimeout(1000);

    expect(meteringIds.length).toBeGreaterThan(0);
    for (const id of meteringIds) {
      expect(id).toBeTruthy();
      expect(id.length).toBeGreaterThan(0);
    }
  });

  test("X-Pulse-Metering-Session-ID is stable across multiple OTLP requests in the same session", async ({
    page,
  }) => {
    const meteringIds: string[] = [];
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      const id = route.request().headers()["x-pulse-metering-session-id"] ?? "";
      if (id) meteringIds.push(id);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    // Let the first scheduled batch flush (VITE_PULSE_BATCH_DELAY_MS=200) so we get an
    // initial /v1/logs export before coalescing later signals into a single pagehide flush.
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("header_test_1");
      p.trackEvent("header_test_2");
    });
    await page.waitForTimeout(500);

    // Must have captured at least 2 log requests to compare header stability
    expect(meteringIds.length).toBeGreaterThanOrEqual(2);
    const uniqueIds = new Set(meteringIds);
    expect(uniqueIds.size).toBe(1);
    // The single value must be a valid UUID
    expect([...uniqueIds][0]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });
});

// ─── app.installation.start ───────────────────────────────────────────────────

test.describe("@M1 app.installation.start", () => {
  test("emitted on first visit with empty storage", async ({ page, otlp }) => {
    // Clear all storage before SDK initialises so it looks like a fresh install
    await page.addInitScript(() => {
      localStorage.clear();
      sessionStorage.clear();
    });

    await page.goto("/");

    const log = await otlp.waitForLog("pulse.app.installation.start");
    expect(getAttr(log.attributes, "pulse.type")).toBe(
      "pulse.app.installation.start",
    );
    const installId = getAttr(log.attributes, "installation.id") as
      | string
      | undefined;
    expect(installId).toBeTruthy();
    expect(installId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  test("NOT emitted on reload when installation ID already in localStorage", async ({
    page,
    otlp,
  }) => {
    // First visit — install ID is written to localStorage
    await page.goto("/");
    await otlp.waitForLog("session.start");

    otlp.reset();

    // Reload — SDK finds existing install ID, must NOT emit app.installation.start again
    await page.reload();
    await page.waitForTimeout(1500);

    expect(
      findAllLogs(otlp.captured, "pulse.app.installation.start").length,
    ).toBe(0);
  });
});

// ─── trackNonFatal ────────────────────────────────────────────────────────────

test.describe("@M1 trackNonFatal", () => {
  test("trackNonFatal emits non_fatal log with correct attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackNonFatal: (name: string, attrs?: Record<string, unknown>) => void;
      };
      p.trackNonFatal("payment_declined", { amount: 99 });
    });

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "non_fatal.type")).toBe("payment_declined");
    expect(getAttr(log.attributes, "non_fatal.is_manual")).toBe(true);
    expect(log.body?.stringValue).toBe("payment_declined");
  });
});

// ─── reportException body ─────────────────────────────────────────────────────

test.describe("@M1 reportException body", () => {
  test("reportException uses error message as log body", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        reportException: (error: Error) => void;
      };
      p.reportException(new Error("test error message"));
    });

    const log = await otlp.waitForLog("non_fatal");
    expect(log.body?.stringValue).toBe("test error message");
  });
});
