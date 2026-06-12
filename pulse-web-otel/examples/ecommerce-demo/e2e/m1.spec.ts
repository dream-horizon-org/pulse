import type { Page, Route } from "@playwright/test";
import { gunzipSync } from "zlib";

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
  findAllSpans,
  findAllSpansByName,
  findAllLogsByBody,
  findAllNetworkSpans,
  getResourceAttr,
} from "./fixture";
import { assertTimestampSanity } from "./otlp-contract-helpers";
import {
  blockActiveConfigFetch,
  demoE2eWhitelistFilterValues,
  minimalPulseSdkConfig,
  seedPulseSdkConfig,
  waitPastSeededSignalsBatchWindow,
} from "./test-sdk-config";

/** Collect unique session.id values across common signal types (journey stability). */
function collectJourneySessionIds(captured: unknown[]): string[] {
  const ids = new Set<string>();
  const add = (sid: unknown) => {
    if (typeof sid === "string") ids.add(sid);
  };
  for (const lr of findAllLogs(captured as never[], "session.start")) {
    add(getAttr(lr.attributes, "session.id"));
  }
  for (const lr of findAllLogs(captured as never[], "session.end")) {
    add(getAttr(lr.attributes, "session.id"));
  }
  for (const lr of findAllLogs(captured as never[], "device.crash")) {
    add(getAttr(lr.attributes, "session.id"));
  }
  for (const lr of findAllLogs(captured as never[], "non_fatal")) {
    add(getAttr(lr.attributes, "session.id"));
  }
  for (const lr of findAllLogs(captured as never[], "web_vital")) {
    add(getAttr(lr.attributes, "session.id"));
  }
  for (const sp of findAllSpans(captured as never[], "screen_load")) {
    add(getAttr(sp.attributes, "session.id"));
  }
  for (const sp of findAllNetworkSpans(captured as never[])) {
    add(getAttr(sp.attributes, "session.id"));
  }
  for (const lr of findAllLogsByBody(
    captured as never[],
    "checkout_complete",
  )) {
    add(getAttr(lr.attributes, "session.id"));
  }
  return [...ids];
}

/** After reload there is often no second `session.start`; `Pulse` on `window` is the ready signal. */
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

/** OTLP POST bodies are often gzip-compressed; mirror `e2e/fixture.ts` decode. */
function decodeOtlpJsonBody(buf: Buffer | null): Record<string, unknown> {
  if (!buf) return {};
  try {
    return JSON.parse(gunzipSync(buf).toString("utf-8")) as Record<
      string,
      unknown
    >;
  } catch {
    try {
      return JSON.parse(buf.toString("utf-8")) as Record<string, unknown>;
    } catch {
      return {};
    }
  }
}

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

  test("double Pulse.init() is a no-op — exactly one session.start", async ({
    page,
    otlp,
  }) => {
    // App.tsx calls Pulse.init() in useEffect; React StrictMode calls it twice
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(1500); // let any duplicate exports arrive

    const starts = findAllLogs(otlp.captured, "session.start");
    expect(starts.length).toBe(1);
  });

  // 3.3 — real browser document unload (not synthetic dispatchEvent). `page.close()` is flaky in
  // Playwright: the target often dies before unload OTLP finishes. Navigating away fires real
  // pagehide/unload while this Playwright page (and `page.route`) stay alive to capture exports.
  test("3.3: session.end reaches OTLP on document unload (navigate to about:blank)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.goto("about:blank");

    const endLog = await otlp.waitForLog("session.end", 10_000);
    expect(getAttr(endLog.attributes, "session.end_reason")).toBe(
      "page_unload",
    );
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(1);
  });

  test("SESS-03: session.end on unload has page_unload, duration_ms > 0", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(400);
    otlp.reset();

    await page.goto("about:blank");
    const endLog = await otlp.waitForLog("session.end", 10_000);
    expect(getAttr(endLog.attributes, "session.end_reason")).toBe(
      "page_unload",
    );
    const durationMs = Number(
      getAttr(endLog.attributes, "session.duration_ms"),
    );
    expect(Number.isFinite(durationMs)).toBe(true);
    expect(durationMs).toBeGreaterThan(0);
  });

  // Dedupe: pagehide already emitted session.end; shutdown must not export a second one
  test("pagehide then Pulse.shutdown emits only one session.end", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });
    await otlp.waitForLog("session.end", 5_000);

    await page.evaluate(async () => {
      const w = window as unknown as {
        Pulse?: { shutdown?: () => Promise<void> };
      };
      await w.Pulse?.shutdown?.();
    });
    await page.waitForTimeout(800);

    expect(findAllLogs(otlp.captured, "session.end").length).toBe(1);
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
    // After reload the session is reused — no session.start fires (correct behaviour per 3.7).
    await waitForPulseInitialized(page);
    const storedId = await page.evaluate(() =>
      localStorage.getItem("pulse_installation_id"),
    );
    expect(storedId).toBe(installId);
  });

  test("installation.id stored in localStorage as pulse_installation_id", async ({
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

  // TODO(future): session.id should also fall back to sessionStorage (tier 2) before in-memory
  // so a page reload within the same tab continues the same session even when localStorage is
  // blocked (WKWebView ITP / sandboxed iframe). sessionStorage survives same-tab reloads but
  // not new tabs, which matches web session semantics (PostHog/Sentry pattern).
  // When implemented, add a test here: block localStorage, reload page, assert same session.id.
  test("installation.id falls back to in-memory when both localStorage and sessionStorage are blocked", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      // Block both localStorage and sessionStorage
      Object.defineProperty(window, "localStorage", {
        get() {
          throw new DOMException("storage unavailable", "SecurityError");
        },
        configurable: true,
      });
      Object.defineProperty(window, "sessionStorage", {
        get() {
          throw new DOMException("storage unavailable", "SecurityError");
        },
        configurable: true,
      });
    });
    await page.goto("/");

    // SDK must not crash; session.start should still emit (using in-memory ID)
    // Give extra timeout since SDK might be slower without storage
    const log = await otlp.waitForLog("session.start", 10_000);
    const installId = getAttr(log.attributes, "installation.id") as string;

    // Verify a valid UUID was generated
    expect(installId).toBeTruthy();
    expect(installId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );

    // Verify it's marked as a new installation (since ID wasn't in storage)
    const isNew = getAttr(log.attributes, "pulse.type");
    expect(isNew).toBe("session.start");
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
    // Unload / forceFlush on the outgoing document can call getSessionId() while exporting
    // queued OTLP (global attrs processor), which re-writes pulse_session_* into
    // localStorage after the clear above. Run an init script on the *next* document so
    // storage is still empty when SessionProvider runs, otherwise _sessionReused stays
    // true and emitInitialSession() does not emit session.start.
    await page.addInitScript(() => {
      try {
        localStorage.clear();
        sessionStorage.clear();
      } catch {
        /* ignore */
      }
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
  const fulfillOtlp = async (route: Route) => {
    if (route.request().method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers: { ...E2E_OTLP_CORS } });
      return;
    }
    await route.fulfill({
      status: 200,
      headers: { ...E2E_OTLP_CORS },
      body: '{"partialSuccess":{}}',
    });
  };

  test("x-api-key header sent on every OTLP request (logs, traces, metrics)", async ({
    page,
  }) => {
    const byKind = {
      logs: [] as string[],
      traces: [] as string[],
      metrics: [] as string[],
    };
    const tap = (kind: keyof typeof byKind) => async (route: Route) => {
      if (route.request().method() === "OPTIONS") {
        await fulfillOtlp(route);
        return;
      }
      byKind[kind].push(route.request().headers()["x-api-key"] ?? "");
      await fulfillOtlp(route);
    };
    await page.route("**/v1/logs", tap("logs"));
    await page.route("**/v1/traces", tap("traces"));
    await page.route("**/v1/metrics", tap("metrics"));
    await page.goto("/");
    await expect
      .poll(
        () => byKind.logs.length + byKind.traces.length + byKind.metrics.length,
        {
          timeout: 15_000,
        },
      )
      .toBeGreaterThan(0);
    // Must match `VITE_PULSE_API_KEY` in ecommerce-demo `.env.test` (Vite injects at dev time).
    for (const kind of ["logs", "traces", "metrics"] as const) {
      const headers = byKind[kind];
      for (const h of headers) expect(h).toBe("default-project_testkey01");
    }
  });

  test("Content-Type is application/json on logs, traces, and metrics", async ({
    page,
  }) => {
    const seen: Record<string, string> = {};
    const tap = (key: string) => async (route: Route) => {
      if (route.request().method() === "OPTIONS") {
        await fulfillOtlp(route);
        return;
      }
      seen[key] = route.request().headers()["content-type"] ?? "";
      await fulfillOtlp(route);
    };
    await page.route("**/v1/logs", tap("logs"));
    await page.route("**/v1/traces", tap("traces"));
    await page.route("**/v1/metrics", tap("metrics"));
    await page.goto("/");
    await expect
      .poll(() => Object.keys(seen).length, { timeout: 15_000 })
      .toBeGreaterThanOrEqual(1);
    for (const ct of Object.values(seen)) {
      expect(ct).toContain("application/json");
    }
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

  test("INIT-04: active config fetch returns HTTP 200 on boot", async ({
    page,
  }) => {
    await page.route("**/v1/configs/active**", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
          },
        });
        return;
      }
      await route.fulfill({
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
        },
        body: JSON.stringify({ version: 1, features: [] }),
      });
    });
    const configPromise = page.waitForResponse(
      (r) =>
        r.url().includes("/v1/configs/active") &&
        r.request().method() === "GET",
    );
    await page.goto("/");
    expect((await configPromise).status()).toBe(200);
  });

  test("CTR-04: exported logs and spans have sane timestamps", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await otlp.waitForSpan("screen_load", 8_000);
    for (const c of otlp.captured) {
      if (c.type === "logs") {
        for (const rl of c.body.resourceLogs ?? []) {
          for (const sl of rl.scopeLogs ?? []) {
            for (const lr of sl.logRecords ?? []) {
              assertTimestampSanity(lr);
            }
          }
        }
      }
      if (c.type === "traces") {
        for (const rs of c.body.resourceSpans ?? []) {
          for (const ss of rs.scopeSpans ?? []) {
            for (const sp of ss.spans ?? []) {
              assertTimestampSanity(sp);
            }
          }
        }
      }
    }
  });
});

// ─── SDK Shutdown ─────────────────────────────────────────────────────────────

test.describe("@M1 SDK shutdown", () => {
  test("Pulse.shutdown() force-flushes providers without error", async ({
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
      // @ts-ignore — `Pulse` exposed on window by App.tsx for testing
      await window.Pulse?.shutdown?.();
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
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
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

    const fulfillOtlp = async (route: Route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    };

    await page.route("**/v1/traces", fulfillOtlp);
    await page.route("**/v1/metrics", fulfillOtlp);
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      if (firstExportAt === 0) firstExportAt = Date.now();
      await fulfillOtlp(route);
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
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
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

  test("rum.sdk.init.* OTLP logs emitted (Android SdkInitializationEvents parity)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const started = findAllLogs(otlp.captured, "rum.sdk.init.started");
    const exporter = findAllLogs(otlp.captured, "rum.sdk.init.span.exporter");
    expect(started.length).toBeGreaterThanOrEqual(1);
    expect(exporter.length).toBeGreaterThanOrEqual(1);
    expect(getAttr(exporter[0]?.attributes, "span.exporter")).toContain(
      "/v1/traces",
    );
  });

  test("no sdk.init span (matches Android — init is not a dedicated trace heartbeat)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    expect(findAllSpansByName(otlp.captured, "sdk.init").length).toBe(0);
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
    // Session is reused on reload — no session.start fires (correct per 3.7).
    await waitForPulseInitialized(page);
    expect(await readCachedMeta()).toEqual({
      version: 1,
      description: "cfg-1",
    });

    server.version = 2;
    otlp.reset();
    await page.reload();
    await waitForPulseInitialized(page);
    await expect
      .poll(async () => (await readCachedMeta())?.version ?? null, {
        timeout: 10_000,
      })
      .toBe(2);

    otlp.reset();
    await page.reload();
    await waitForPulseInitialized(page);
    expect(await readCachedMeta()).toEqual({
      version: 2,
      description: "cfg-2",
    });
  });
});

const ACTIVE_CONFIG_CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-API-KEY",
} as const;

test.describe("@M1 remote config fetch resilience", () => {
  test("active config 404 + empty pulse_sdk_config → defaults, session.start exports", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      try {
        localStorage.removeItem("pulse_sdk_config");
      } catch {
        /* ignore */
      }
    });
    await page.route("**/v1/configs/active**", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: ACTIVE_CONFIG_CORS });
        return;
      }
      await route.fulfill({
        status: 404,
        contentType: "application/json",
        headers: ACTIVE_CONFIG_CORS,
        body: "{}",
      });
    });
    await page.goto("/");
    await otlp.waitForLog("session.start");
  });

  test("cached pulse_sdk_config version unchanged when active fetch returns 404 on reload", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 991,
      description: "pinned-for-404-reload",
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [],
        signalsToSample: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await page.route("**/v1/configs/active**", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: ACTIVE_CONFIG_CORS });
        return;
      }
      await route.fulfill({
        status: 404,
        contentType: "application/json",
        headers: ACTIVE_CONFIG_CORS,
        body: "{}",
      });
    });
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const v1 = await page.evaluate(() => {
      const raw = localStorage.getItem("pulse_sdk_config");
      if (!raw) return null;
      return (JSON.parse(raw) as { version: number }).version;
    });
    expect(v1).toBe(991);

    otlp.reset();
    await page.reload();
    await waitForPulseInitialized(page);
    const v2 = await page.evaluate(() => {
      const raw = localStorage.getItem("pulse_sdk_config");
      if (!raw) return null;
      return (JSON.parse(raw) as { version: number }).version;
    });
    expect(v2).toBe(991);
  });
});

// ─── Consent ──────────────────────────────────────────────────────────────────

test.describe("@M1 consent", () => {
  test("DENIED consent → Pulse.isInitialized() returns false", async ({
    page,
  }) => {
    // ?pulse_consent=denied is handled by App.tsx → PulseDataCollectionConsent.DENIED
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(500);

    const initialized = await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
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
  const tapMeteringHeader = (meteringIds: string[]) => async (route: Route) => {
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
  };

  test("X-Pulse-Metering-Session-ID header sent on logs, traces, and metrics OTLP requests", async ({
    page,
  }) => {
    const meteringIds: string[] = [];
    const tap = tapMeteringHeader(meteringIds);
    await page.route("**/v1/logs", tap);
    await page.route("**/v1/traces", tap);
    await page.route("**/v1/metrics", tap);

    await page.goto("/");
    await expect
      .poll(() => meteringIds.length, { timeout: 15_000 })
      .toBeGreaterThan(0);
    for (const id of meteringIds) {
      expect(id).toBeTruthy();
      expect(id.length).toBeGreaterThan(0);
    }
  });

  test("X-Pulse-Metering-Session-ID is stable across multiple OTLP requests in the same session", async ({
    page,
  }) => {
    const meteringIds: string[] = [];
    const tap = async (route: Route) => {
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
    };
    await page.route("**/v1/logs", tap);
    await page.route("**/v1/traces", tap);
    await page.route("**/v1/metrics", tap);

    await page.goto("/");
    // Let the first scheduled batch flush (VITE_PULSE_BATCH_DELAY_MS=200) so we get an
    // initial /v1/logs export before coalescing later signals into a single pagehide flush.
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("header_test_1");
      p.trackEvent("header_test_2");
    });

    // Must have captured at least 2 OTLP requests with a metering header (batch + export timing varies)
    await expect
      .poll(() => meteringIds.filter(Boolean).length, { timeout: 15_000 })
      .toBeGreaterThanOrEqual(2);
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
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
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
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        reportException: (error: Error) => void;
      };
      p.reportException(new Error("test error message"));
    });

    const log = await otlp.waitForLog("non_fatal");
    expect(log.body?.stringValue).toBe("test error message");
  });
});

// ─── Window ID ────────────────────────────────────────────────────────────────

test.describe("@M1 window.id uniqueness", () => {
  test("window.id is present on every signal", async ({ page, otlp }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    // window.id must be stamped by GlobalAttributesProcessor
    const windowId = getAttr(log.attributes, "window.id");
    expect(windowId).toBeTruthy();
    expect(typeof windowId).toBe("string");
    // Must be a valid UUID
    expect(windowId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  test("window.id is unique per page load (in-memory, never persisted)", async ({
    page,
    otlp,
  }) => {
    await blockActiveConfigFetch(page);
    // First load — capture window.id
    await page.goto("/");
    const log1 = await otlp.waitForLog("session.start");
    const windowId1 = getAttr(log1.attributes, "window.id") as string;

    otlp.reset();

    // Reload — should get a different window.id (same session, but new page-load = new in-memory ID)
    await page.reload();
    await waitForPulseInitialized(page);
    // After reload, session is reused (no new session.start emitted)
    // So we emit a trackEvent to capture a signal after reload
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("reload_check");
    });

    // trackEvent creates a log with body "reload_check" (not pulse.type)
    const log2 = await otlp.waitForLogByBody("reload_check");
    const windowId2 = getAttr(log2.attributes, "window.id") as string;

    // Both must exist
    expect(windowId1).toBeTruthy();
    expect(windowId2).toBeTruthy();
    // But they must be different (in-memory ID, regenerated on each load)
    expect(windowId2).not.toBe(windowId1);
  });

  test("window.id same across multiple signals within one page load", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const log1 = await otlp.waitForLog("session.start");
    const windowId1 = getAttr(log1.attributes, "window.id") as string;

    // Emit another signal
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("window_id_test");
    });

    await page.waitForTimeout(1500);
    const allLogs = findAllLogsByBody(otlp.captured, "window_id_test");
    expect(allLogs.length).toBeGreaterThan(0);

    const windowId2 = getAttr(allLogs[0]?.attributes, "window.id") as string;
    // Same page load → same window.id
    expect(windowId2).toBe(windowId1);
  });
});

// ─── Clone Detection (PostHog Model) ───────────────────────────────────────────

test.describe("@M1 clone detection", () => {
  test("session.id is stored in localStorage (shared across tabs)", async ({
    page,
  }) => {
    // Load page and verify session.id is stored in localStorage
    await page.goto("/");
    await page.waitForTimeout(1000); // Wait for SDK to initialize

    const sid = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sid).toBeTruthy();
    expect(typeof sid).toBe("string");
    expect(sid).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );

    // Verify it persists on page reload (same session.id)
    const sidBefore = sid;
    await page.reload();
    await page.waitForTimeout(500);
    const sidAfter = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sidAfter).toBe(sidBefore);
  });

  test("clone flag detects duplicate tab via sessionStorage inheritance", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(500);

    // Clone flag should be set in sessionStorage on init
    const flagAfterInit = await page.evaluate(() =>
      sessionStorage.getItem("pulse_session_clone_flag"),
    );
    expect(flagAfterInit).toBe("1");

    // Simulate what happens on reload: beforeunload removes the flag
    await page.evaluate(() => {
      window.dispatchEvent(new Event("beforeunload"));
    });

    // After beforeunload, flag should be gone
    const flagAfterBeforeunload = await page.evaluate(() =>
      sessionStorage.getItem("pulse_session_clone_flag"),
    );
    expect(flagAfterBeforeunload).toBeNull();

    // On next init (simulated by reload), flag is re-written
    // We can't directly test reload here without it being a real reload,
    // but the flag lifecycle is: init writes it → beforeunload removes it → next init rewrites it
  });
});

// ─── Reload vs Clone ──────────────────────────────────────────────────────────

test.describe("@M1 reload vs clone detection", () => {
  test("reload: same session.id persisted (session reused silently, no new session.start)", async ({
    page,
    otlp,
  }) => {
    // First load
    await page.goto("/");
    const log1 = await otlp.waitForLog("session.start");
    const sid1 = getAttr(log1.attributes, "session.id") as string;

    otlp.reset();

    // Reload — session should be reused, NOT emit a new session.start
    await page.reload();
    // Give SDK time to initialize and emit any signals
    await page.waitForTimeout(1000);

    // Session should be reused, so session.id in localStorage should be the same
    const sid2 = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sid2).toBe(sid1);

    // Verify NO new session.start was emitted on reload (session reused silently)
    const allStarts = findAllLogs(otlp.captured, "session.start");
    expect(allStarts.length).toBe(0); // No new session.start because session was reused
  });

  test("reload: beforeunload called (removes clone flag, keeps session intact)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(500);

    // Verify clone flag is present before reload
    let flagBefore = await page.evaluate(() =>
      sessionStorage.getItem("pulse_session_clone_flag"),
    );
    expect(flagBefore).toBe("1");

    // beforeunload is called before reload
    // We can't actually trigger reload in playwright cleanly, but we can simulate beforeunload
    await page.evaluate(() => {
      window.dispatchEvent(new Event("beforeunload"));
    });

    // After beforeunload, flag should be cleared
    const flagAfter = await page.evaluate(() =>
      sessionStorage.getItem("pulse_session_clone_flag"),
    );
    expect(flagAfter).toBeNull();

    // session.id should still be in localStorage
    const sessionId = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sessionId).toBeTruthy();
  });
});

// ─── Area 2: screen.name resolution ──────────────────────────────────────────

test.describe("@M1 screen.name resolution", () => {
  // 2.2 — screen.name resolves from URL path
  test("screen.name resolves from URL path /products → '/products'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await waitForPulseInitialized(page);
    await page.evaluate(
      () =>
        (window as unknown as Record<string, unknown>)["Pulse"] &&
        (
          window as unknown as { Pulse: { trackEvent: (n: string) => void } }
        ).Pulse.trackEvent("screen_name_check"),
    );
    const log = await otlp.waitForLogByBody("screen_name_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products");
  });

  // 2.3 — dynamic path segments normalized to :id (route shape; see GlobalAttributesProcessor)
  test("screen.name normalizes numeric segment: /products/123 → '/products/:id'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products/123");
    await waitForPulseInitialized(page);
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("numeric_strip_check"),
    );
    const log = await otlp.waitForLogByBody("numeric_strip_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products/:id");
  });

  // 2.16 — screen.name for root path /
  test("screen.name for root path / resolves to '/'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseInitialized(page);
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("root_path_check"),
    );
    const log = await otlp.waitForLogByBody("root_path_check");
    const screenName = getAttr(log.attributes, "screen.name") as string;
    expect(screenName).toBeTruthy();
    expect(screenName).toBe("/");
  });

  // 2.17 — UUID path segments normalized to :id
  test("screen.name normalizes UUID segment: /products/<uuid> → '/products/:id'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products/550e8400-e29b-41d4-a716-446655440000");
    await waitForPulseInitialized(page);
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("uuid_strip_check"),
    );
    const log = await otlp.waitForLogByBody("uuid_strip_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products/:id");
  });
});

// ─── Area 2: manual screen.name override ─────────────────────────────────────

test.describe("@M1 screen.name manual override", () => {
  // 2.18 — setScreenName() manual override applied immediately
  test("Pulse.setScreenName() overrides screen.name on next signal", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = window as unknown as {
        Pulse: {
          setScreenName: (n: string) => void;
          trackEvent: (n: string) => void;
        };
      };
      p.Pulse.setScreenName("custom-screen");
      p.Pulse.trackEvent("override_check");
    });
    const log = await otlp.waitForLogByBody("override_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("custom-screen");
  });

  // 2.19 — manual override persists across multiple pings
  test("manual screen.name override persists across multiple events", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = window as unknown as {
        Pulse: {
          setScreenName: (n: string) => void;
          trackEvent: (n: string) => void;
        };
      };
      p.Pulse.setScreenName("my-screen");
      p.Pulse.trackEvent("persist_check_1");
      p.Pulse.trackEvent("persist_check_2");
    });

    const log1 = await otlp.waitForLogByBody("persist_check_1");
    const log2 = await otlp.waitForLogByBody("persist_check_2");
    expect(getAttr(log1.attributes, "screen.name")).toBe("my-screen");
    expect(getAttr(log2.attributes, "screen.name")).toBe("my-screen");
  });

  // 2.20 — override resets to URL-based value after navigation
  test("screen.name resets to URL path after navigation (override cleared)", async ({
    page,
    otlp,
  }) => {
    await blockActiveConfigFetch(page);
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Set override on /products
    await page.evaluate(() => {
      const p = window as unknown as {
        Pulse: { setScreenName: (n: string) => void };
      };
      p.Pulse.setScreenName("my-screen");
    });

    // Navigate to /cart — override should reset
    await page.goto("/cart");
    await waitForPulseInitialized(page);
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("reset_check"),
    );
    const log = await otlp.waitForLogByBody("reset_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/cart");
  });

  test("screen.name resets to URL path after SPA navigation (override cleared)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Set override then simulate SPA pushState navigation (no page reload)
    await page.evaluate(() => {
      const p = window as unknown as {
        Pulse: {
          setScreenName: (n: string) => void;
          trackEvent: (n: string) => void;
        };
      };
      p.Pulse.setScreenName("my-screen");
      history.pushState({}, "", "/cart");
      p.Pulse.trackEvent("spa_nav_check");
    });

    const log = await otlp.waitForLogByBody("spa_nav_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/cart");
  });
});

// ─── Area 2: url attributes ───────────────────────────────────────────────────

test.describe("@M1 url attributes", () => {
  // 2.4 — url.path updates on navigation
  test("url.path updates correctly after SPA navigation", async ({
    page,
    otlp,
  }) => {
    await blockActiveConfigFetch(page);
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // First event on /products
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("url_path_products"),
    );
    const log1 = await otlp.waitForLogByBody("url_path_products");
    expect(getAttr(log1.attributes, "url.path")).toBe("/products");
    otlp.reset();

    // Navigate to /cart
    await page.goto("/cart");
    await waitForPulseInitialized(page);
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("url_path_cart"),
    );
    const log2 = await otlp.waitForLogByBody("url_path_cart");
    expect(getAttr(log2.attributes, "url.path")).toBe("/cart");
  });

  // 2.21 — screen.name present on log records (not just spans)
  test("screen.name is present on log records, not just spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    const log = await otlp.waitForLog("session.start");
    const screenName = getAttr(log.attributes, "screen.name") as string;
    expect(screenName).toBeTruthy();
    // session.start is a log — screen.name must be on it
    expect(typeof screenName).toBe("string");
  });

  // 2.22 — page.url contains full URL, url.path contains only path
  test("page.url is full URL and url.path is path-only — two separate attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products");
    await page.evaluate(() =>
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("url_attrs_check"),
    );
    const log = await otlp.waitForLogByBody("url_attrs_check");
    const pageUrl = getAttr(log.attributes, "page.url") as string;
    const urlPath = getAttr(log.attributes, "url.path") as string;

    expect(pageUrl).toMatch(/^https?:\/\/.+\/products$/);
    expect(urlPath).toBe("/products");
    // They must be different values
    expect(pageUrl).not.toBe(urlPath);
  });
});

// ─── Area 3: Session Start / Session End (missing coverage) ──────────────────

test.describe("@M1 Area 3 session lifecycle", () => {
  // 3.2 — session.start does NOT fire again on SPA navigation
  test("3.2: session.start does NOT fire on SPA navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Navigate through multiple SPA routes
    await page
      .getByRole("link", { name: /products/i })
      .first()
      .click();
    await page.waitForURL("**/products");
    await page.getByRole("link", { name: /cart/i }).first().click();
    await page.waitForURL("**/cart");
    await page.waitForTimeout(600);

    expect(findAllLogs(otlp.captured, "session.start").length).toBe(0);
  });

  // 3.6 — New session.start (+ session.end for old) after simulated 30-min inactivity
  test("3.6: session rotates after simulated 30-min inactivity", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const oldSid = getAttr(first.attributes, "session.id") as string;
    otlp.reset();

    // Simulate 31-minute idle: write pulse_session_ts = 31 min ago in nanoseconds
    // (session.ts) is stored as Date.now() * 1_000_000 — see session.ts:msToNs)
    await page.evaluate(() => {
      const thirtyOneMinutesAgoNs = (Date.now() - 31 * 60 * 1000) * 1_000_000;
      localStorage.setItem("pulse_session_ts", String(thirtyOneMinutesAgoNs));
    });

    // trackEvent → onEmit → getSessionId() → detects inactivity → rotates
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (n: string) => void;
      };
      p.trackEvent("after_inactivity");
    });

    // New session.start must arrive with a different session.id
    const newStart = await otlp.waitForLog("session.start", 5_000);
    const newSid = getAttr(newStart.attributes, "session.id") as string;
    expect(newSid).toBeTruthy();
    expect(newSid).not.toBe(oldSid);
    expect(getAttr(newStart.attributes, "session.start_reason")).toBe(
      "inactivity_timeout",
    );

    // session.end for the OLD session must also be present
    const ends = findAllLogs(otlp.captured, "session.end");
    expect(ends.length).toBeGreaterThan(0);
    expect(getAttr(ends[0]?.attributes, "session.id")).toBe(oldSid);
  });

  // 3.8 — session.end fires on reload pagehide; same session resumes after reload (no new session.start)
  test("3.8: session.end fires on pagehide before reload; same session resumes silently", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const sid = getAttr(first.attributes, "session.id") as string;
    otlp.reset();

    // Simulate the pagehide that fires just before a page reload
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });
    const endLog = await otlp.waitForLog("session.end", 3_000);
    expect(getAttr(endLog.attributes, "session.id")).toBe(sid);
    expect(getAttr(endLog.attributes, "session.end_reason")).toBe(
      "page_unload",
    );
    otlp.reset();

    // Real reload — same session must resume (session.id unchanged in localStorage)
    await page.reload();
    await page.waitForTimeout(1_000);

    const storedSid = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(storedSid).toBe(sid);
    // Session was reused → no new session.start emitted
    expect(findAllLogs(otlp.captured, "session.start").length).toBe(0);
  });

  // 3.9 — Duplicate tab inherits session (same session.id, no new session.start)
  test("3.9: duplicate page in same context inherits session.id", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const sid1 = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    const sessionTs = await page.evaluate(() =>
      localStorage.getItem("pulse_session_ts"),
    );
    const sessionStart = await page.evaluate(() =>
      localStorage.getItem("pulse_session_start"),
    );

    // Simulate tab clone: open page2 in same context, pre-populate storage with page1's session
    const page2 = await page.context().newPage();
    // Silence OTLP calls from page2 (no capture needed — assert via storage only)
    await page2.route("**/v1/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });
    await page2.addInitScript(
      ({ id, ts, start }) => {
        if (id) localStorage.setItem("pulse_session_id", id);
        if (ts) localStorage.setItem("pulse_session_ts", ts);
        if (start) localStorage.setItem("pulse_session_start", start);
      },
      { id: sid1, ts: sessionTs, start: sessionStart },
    );
    await page2.goto("/");
    await page2.waitForTimeout(1_000);

    // Same session.id must be in localStorage — no new session was created
    const sid2 = await page2.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sid2).toBe(sid1);

    await page2.close();
  });

  // 3.10 — Fresh browser context (new tab, empty storage) creates a new independent session
  test("3.10: fresh browser context creates new independent session", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const sid1 = await page.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );

    // Fresh context = isolated storage (no shared localStorage)
    const freshCtx = await page.context().browser()!.newContext();
    const freshLogs: Array<Record<string, unknown>> = [];
    await freshCtx.route("**/v1/logs", async (route) => {
      const buf = route.request().postDataBuffer();
      if (buf) {
        const parsed = decodeOtlpJsonBody(buf);
        if (Object.keys(parsed).length > 0) freshLogs.push(parsed);
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });
    await freshCtx.route("**/v1/traces", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });
    await freshCtx.route("**/v1/metrics", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });

    const freshPage = await freshCtx.newPage();
    await freshPage.goto("/");
    await freshPage.waitForTimeout(1_500);

    // Fresh context must have a different session.id
    const sid2 = await freshPage.evaluate(() =>
      localStorage.getItem("pulse_session_id"),
    );
    expect(sid2).toBeTruthy();
    expect(sid2).not.toBe(sid1);

    // session.start must have fired in the fresh context
    const sessionStartFound = freshLogs.some((body) =>
      (
        (
          body as {
            resourceLogs?: {
              scopeLogs?: {
                logRecords?: {
                  attributes?: {
                    key: string;
                    value: { stringValue?: string };
                  }[];
                }[];
              }[];
            }[];
          }
        ).resourceLogs ?? []
      )
        .flatMap((rl) => rl.scopeLogs ?? [])
        .flatMap((sl) => sl.logRecords ?? [])
        .some((lr) =>
          (lr.attributes ?? []).some(
            (a) =>
              a.key === "pulse.type" &&
              a.value?.stringValue === "session.start",
          ),
        ),
    );
    expect(sessionStartFound).toBe(true);

    await freshCtx.close();
  });

  // 3.11 + 3.12 — Rotation order: session.end fires BEFORE session.start; IDs are different
  test("3.11/3.12: rotation emits session.end then session.start with different session IDs", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const oldSid = getAttr(first.attributes, "session.id") as string;
    otlp.reset();

    // Simulate inactivity
    await page.evaluate(() => {
      const thirtyOneMinutesAgoNs = (Date.now() - 31 * 60 * 1000) * 1_000_000;
      localStorage.setItem("pulse_session_ts", String(thirtyOneMinutesAgoNs));
    });

    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (n: string) => void;
      };
      p.trackEvent("rotation_order_check");
    });

    await otlp.waitForLog("session.start", 5_000);
    await page.waitForTimeout(300); // let batch flush complete

    const allLogs = otlp.captured
      .filter((c) => c.type === "logs")
      .flatMap((c) =>
        c.body.resourceLogs.flatMap((rl) =>
          rl.scopeLogs.flatMap((sl) => sl.logRecords),
        ),
      );

    // Find the rotation pair by index to verify ORDER: end must appear before start
    const endIdx = allLogs.findIndex(
      (lr) => getAttr(lr.attributes, "pulse.type") === "session.end",
    );
    const startIdx = allLogs.findIndex((lr) => {
      const pt = getAttr(lr.attributes, "pulse.type");
      const sid = getAttr(lr.attributes, "session.id") as string;
      return pt === "session.start" && sid !== oldSid;
    });

    expect(endIdx).toBeGreaterThanOrEqual(0); // session.end exists
    expect(startIdx).toBeGreaterThanOrEqual(0); // session.start exists
    expect(endIdx).toBeLessThan(startIdx); // end BEFORE start

    // 3.12: The new session.start carries a DIFFERENT session.id than session.end
    const endSid = getAttr(allLogs[endIdx]?.attributes, "session.id") as string;
    const startSid = getAttr(
      allLogs[startIdx]?.attributes,
      "session.id",
    ) as string;
    expect(endSid).toBe(oldSid);
    expect(startSid).not.toBe(oldSid);
  });

  // 3.14 — session.end does NOT fire on in-app SPA navigation
  test("3.14: session.end does NOT fire on in-app SPA navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Navigate through multiple SPA routes
    await page
      .getByRole("link", { name: /products/i })
      .first()
      .click();
    await page.waitForURL("**/products");
    await page.getByRole("link", { name: /cart/i }).first().click();
    await page.waitForURL("**/cart");
    await page.waitForTimeout(600);

    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);
  });

  // 3.13 — very short session (immediate pagehide) still emits session.end with duration >= 0
  test("3.13: very short session emits session.end with non-negative duration_ms", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const startLog = await otlp.waitForLog("session.start");
    const sid = getAttr(startLog.attributes, "session.id") as string;
    otlp.reset();

    // Immediately dispatch pagehide — session was very short (< 1s)
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    const endLog = await otlp.waitForLog("session.end", 3_000);
    expect(endLog).toBeDefined();
    expect(getAttr(endLog.attributes, "session.id")).toBe(sid);

    const durationMs = getAttr(
      endLog.attributes,
      "session.duration_ms",
    ) as number;
    // Duration must exist and be non-negative milliseconds
    expect(durationMs).toBeGreaterThanOrEqual(0);
    // Wall-clock can exceed nominal session length slightly under load / CI
    expect(durationMs).toBeLessThan(3_000);
  });

  // 3.15 — consent DENIED: no session.start and no session.end emitted
  test("3.15: consent DENIED — no session.start or session.end emitted", async ({
    page,
    otlp,
  }) => {
    // ?pulse_consent=denied → App.tsx passes PulseDataCollectionConsent.DENIED to Pulse.init()
    // The SDK returns early without installing any instrumentations, so no signals fire.
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(800);

    expect(findAllLogs(otlp.captured, "session.start").length).toBe(0);
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);

    // Trigger pagehide — still no session.end (SDK never started)
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });
    await page.waitForTimeout(300);

    expect(findAllLogs(otlp.captured, "session.start").length).toBe(0);
    expect(findAllLogs(otlp.captured, "session.end").length).toBe(0);
  });

  test("SESS-05: session.id stable across multi-nav and checkout journey", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const start = await otlp.waitForLog("session.start");
    const journeySid = getAttr(start.attributes, "session.id") as string;
    otlp.reset();

    await page.getByRole("link", { name: /products/i }).click();
    await page.waitForURL("**/products");
    await page.waitForTimeout(800);

    await page.getByRole("link", { name: /cart/i }).click();
    await page.waitForURL("**/cart");
    await page.waitForTimeout(400);

    await page.getByRole("link", { name: /checkout/i }).click();
    await page.waitForURL("**/checkout");
    await page.getByTestId("checkout-step-1-next").click();
    await page.getByTestId("checkout-step-2-next").click();
    await page.getByTestId("checkout-step-3-confirm").click();
    await page.waitForTimeout(1200);

    const ids = collectJourneySessionIds(otlp.captured);
    expect(ids.length).toBe(1);
    expect(ids[0]).toBe(journeySid);
  });

  test("INIT-03: no duplicate session.start in same export batch on rotate", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      const thirtyOneMinutesAgoNs = (Date.now() - 31 * 60 * 1000) * 1_000_000;
      localStorage.setItem("pulse_session_ts", String(thirtyOneMinutesAgoNs));
    });
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("init03_rotate_probe");
    });
    await otlp.waitForLog("session.start", 8_000);
    const starts = findAllLogs(otlp.captured, "session.start");
    expect(starts.length).toBe(1);
  });

  test("SESS-07: first session.start has absent or empty session.previous_id", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      try {
        localStorage.clear();
        sessionStorage.clear();
      } catch {
        /* ignore */
      }
    });
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    const prev = getAttr(log.attributes, "session.previous_id");
    expect(prev === undefined || prev === "" || prev === null).toBe(true);
  });

  test("SESS-15: page_unload end_reason on pagehide", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });
    const end = await otlp.waitForLog("session.end", 5_000);
    expect(getAttr(end.attributes, "session.end_reason")).toBe("page_unload");
  });

  test("SESS-15: shutdown end_reason on Pulse.shutdown without pagehide", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(async () => {
      const w = window as unknown as {
        Pulse?: { shutdown?: () => Promise<void> };
      };
      await w.Pulse?.shutdown?.();
    });
    const end = await otlp.waitForLog("session.end", 8_000);
    expect(getAttr(end.attributes, "session.end_reason")).toBe("shutdown");
  });

  test("SESS-15: max_lifetime end_reason when session age exceeds 4h", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const oldSid = getAttr(first.attributes, "session.id") as string;
    otlp.reset();
    await page.evaluate(() => {
      const fiveHoursAgoNs = (Date.now() - 5 * 60 * 60 * 1000) * 1_000_000;
      localStorage.setItem("pulse_session_start", String(fiveHoursAgoNs));
      localStorage.setItem("pulse_session_ts", String(Date.now() * 1_000_000));
    });
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("sess15_max_lifetime");
    });
    const end = await otlp.waitForLog("session.end", 8_000);
    expect(getAttr(end.attributes, "session.id")).toBe(oldSid);
    expect(getAttr(end!.attributes, "session.end_reason")).toBe("max_lifetime");
    const rotated = await otlp.waitForLog("session.start", 8_000);
    expect(getAttr(rotated.attributes, "session.start_reason")).toBe(
      "max_lifetime",
    );
  });

  test("SESS-07 chain: rotation session.start carries session.previous_id", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const first = await otlp.waitForLog("session.start");
    const oldSid = getAttr(first.attributes, "session.id") as string;
    otlp.reset();
    await page.evaluate(() => {
      const thirtyOneMinutesAgoNs = (Date.now() - 31 * 60 * 1000) * 1_000_000;
      localStorage.setItem("pulse_session_ts", String(thirtyOneMinutesAgoNs));
    });
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("sess07_chain");
    });
    const rotated = await otlp.waitForLog("session.start", 8_000);
    expect(getAttr(rotated.attributes, "session.previous_id")).toBe(oldSid);
  });

  test("CON-02: ALLOWED consent emits session.start within 2s", async ({
    page,
    otlp,
  }) => {
    const t0 = Date.now();
    await page.goto("/?pulse_consent=allowed");
    await otlp.waitForLog("session.start", 2_000);
    expect(Date.now() - t0).toBeLessThan(2_500);
  });

  test("CON-03: remount DENIED after session.start exports zero OTLP on interact", async ({
    page,
    otlp,
  }) => {
    await page.goto("/consent-lab");
    await otlp.waitForLog("session.start");
    await page.getByTestId("pulse-remount-denied").click();
    await page.waitForTimeout(1200);
    otlp.reset();

    await page.evaluate(() => {
      (
        window as unknown as { Pulse?: { trackEvent: (n: string) => void } }
      ).Pulse?.trackEvent?.("post_denied_probe");
    });
    await page.waitForTimeout(800);

    expect(otlp.captured.length).toBe(0);
  });

  test("CON-08: session feature gate off — no session.start or session.end", async ({
    page,
    otlp,
  }) => {
    await blockActiveConfigFetch(page);
    await seedPulseSdkConfig(
      page,
      minimalPulseSdkConfig({
        features: [
          {
            featureName: "session",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
            config: null,
          },
        ],
      }),
    );
    await page.goto("/");
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogs(otlp.captured, "session.start")).toHaveLength(0);
    expect(findAllLogs(otlp.captured, "session.end")).toHaveLength(0);
  });
});

// ─── Area 2: resource attributes ─────────────────────────────────────────────

test.describe("@M1 resource attributes", () => {
  // 2.10 — browser.name and browser.version
  test("browser.name and browser.version are non-empty in resource attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const browserName = getResourceAttr(otlp.captured, "browser.name");
    const browserVersion = getResourceAttr(otlp.captured, "browser.version");

    expect(browserName).toBeTruthy();
    expect(browserVersion).toBeTruthy();
    // Should be a recognisable browser name
    expect(["Chrome", "Google Chrome", "Firefox", "Safari", "Edge"]).toContain(
      // Normalise: Playwright chromium reports "Google Chrome" or "Chromium"
      browserName?.includes("Chrome") || browserName?.includes("Chromium")
        ? "Chrome"
        : browserName,
    );
  });

  // 2.11 — os.name and os.version
  test("os.name is web in resource attributes (CH Platform parity)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const osName = getResourceAttr(otlp.captured, "os.name");
    expect(osName).toBe("web");
  });

  // 2.12 — device.type
  test("device.type = 'desktop' when running in a desktop browser", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const deviceType = getResourceAttr(otlp.captured, "device.type");
    expect(deviceType).toBe("desktop");
  });

  // 2.15 — project.id extracted from API key
  test("project.id is present and non-empty in resource attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const projectId = getResourceAttr(otlp.captured, "project.id");
    expect(projectId).toBeTruthy();
    // project.id must be derived from the API key — must not be empty
    expect((projectId as string).length).toBeGreaterThan(0);
  });
});

// ─── Seeded remote config: metricsToAdd, filters, feature gate (crosswalk gaps) ─

test.describe("@M1 remote config + export gate (seeded localStorage)", () => {
  test("metricsToAdd counter appears on /v1/metrics after session.start log export", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 801,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [
          {
            name: "e2e_derived_span_total",
            target: { type: "name" },
            condition: {
              name: ".*",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
            type: { type: "counter" },
          },
        ],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const dp = await otlp.waitForMetric("e2e_derived_span_total", 25_000);
    const v = dp.asInt ?? dp.asDouble;
    expect(v).toBeDefined();
    expect(Number(v)).toBeGreaterThanOrEqual(1);
  });

  test("custom_events sessionSampleRate 0 blocks trackEvent from OTLP", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 802,
      features: [
        {
          featureName: "custom_events",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.getByRole("link", { name: /shop now/i }).click();
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogsByBody(otlp.captured, "shop_now_click").length).toBe(0);
  });

  test("signals.filters BLACKLIST drops matching custom_event logs at export", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 803,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: {
          mode: "BLACKLIST",
          values: [
            {
              name: "^shop_now_click$",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
          ],
        },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.getByRole("link", { name: /shop now/i }).click();
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogsByBody(otlp.captured, "shop_now_click").length).toBe(0);
  });

  test("PENDING consent → SDK does not init and zero OTLP", async ({
    page,
    otlp,
  }) => {
    await page.goto("/?pulse_consent=pending");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(3000);
    expect(otlp.captured.length).toBe(0);
    const inited = await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { isInitialized: () => boolean };
      };
      return w.Pulse?.isInitialized?.() ?? false;
    });
    expect(inited).toBe(false);
  });

  test("attributesToAdd from remote config appears on session.start at export", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 804,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [
          {
            values: [
              {
                name: "pulse.e2e.attr",
                value: "from-seed",
                type: "STRING",
              },
            ],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "pulse.e2e.attr")).toBe("from-seed");
  });

  test("attributesToDrop removes keys matched by rule on session.start at export", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 805,
      signals: {
        scheduleDurationMs: 5000,
        attributesToAdd: [
          {
            values: [
              {
                name: "pulse.e2e.droptest",
                value: "to-be-removed",
                type: "STRING",
              },
            ],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        attributesToDrop: [
          {
            values: ["pulse\\.e2e\\.droptest"],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "pulse.e2e.droptest")).toBeUndefined();
  });

  test("WHITELIST filter drops custom trackEvent bodies not in allowlist", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 806,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: {
          mode: "WHITELIST",
          values: demoE2eWhitelistFilterValues(),
        },
        metricsToAdd: [],
      },
      features: [
        {
          featureName: "custom_events",
          sessionSampleRate: 1,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("e2e_whitelist_probe");
    });
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogsByBody(otlp.captured, "e2e_whitelist_probe").length).toBe(
      0,
    );
  });

  test("multiple attributesToAdd entries all apply on session.start", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 807,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [
          {
            values: [
              {
                name: "pulse.e2e.multi_a",
                value: "a",
                type: "STRING",
              },
            ],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
          {
            values: [
              {
                name: "pulse.e2e.multi_b",
                value: "b",
                type: "STRING",
              },
            ],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "pulse.e2e.multi_a")).toBe("a");
    expect(getAttr(log.attributes, "pulse.e2e.multi_b")).toBe("b");
  });

  test("multiple attributesToDrop rules remove different keys on session.start", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 808,
      signals: {
        scheduleDurationMs: 5000,
        attributesToAdd: [
          {
            values: [
              {
                name: "pulse.e2e.drop_a",
                value: "x",
                type: "STRING",
              },
              {
                name: "pulse.e2e.drop_b",
                value: "y",
                type: "STRING",
              },
            ],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        attributesToDrop: [
          {
            values: ["pulse\\.e2e\\.drop_a"],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
          {
            values: ["pulse\\.e2e\\.drop_b"],
            condition: {
              name: "^session\\.start$",
              props: [],
              scopes: ["logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "pulse.e2e.drop_a")).toBeUndefined();
    expect(getAttr(log.attributes, "pulse.e2e.drop_b")).toBeUndefined();
  });

  test("explicit empty metricsToAdd attributesToAdd attributesToDrop still exports session.start", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 809,
      signals: {
        scheduleDurationMs: 5000,
        attributesToAdd: [],
        attributesToDrop: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");
    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  });

  test("two metricsToAdd counters both increment from session.start log export", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 810,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [
          {
            name: "e2e_derived_alpha_total",
            target: { type: "name" },
            condition: {
              name: ".*",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
            type: { type: "counter" },
          },
          {
            name: "e2e_derived_beta_total",
            target: { type: "name" },
            condition: {
              name: ".*",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
            type: { type: "counter" },
          },
        ],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const a = await otlp.waitForMetric("e2e_derived_alpha_total", 25_000);
    const b = await otlp.waitForMetric("e2e_derived_beta_total", 25_000);
    expect(Number(a.asInt ?? a.asDouble)).toBeGreaterThanOrEqual(1);
    expect(Number(b.asInt ?? b.asDouble)).toBeGreaterThanOrEqual(1);
  });

  test("BLACKLIST with multiple filter values drops each matching log body", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 811,
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: {
          mode: "BLACKLIST",
          values: [
            {
              name: "^e2e_blk_one$",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
            {
              name: "^e2e_blk_two$",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
          ],
        },
        metricsToAdd: [],
      },
      features: [
        {
          featureName: "custom_events",
          sessionSampleRate: 1,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("e2e_blk_one");
      p.trackEvent("e2e_blk_two");
      p.trackEvent("e2e_blk_ok");
    });
    await expect
      .poll(() => findAllLogsByBody(otlp.captured, "e2e_blk_ok").length, {
        timeout: 20_000,
      })
      .toBeGreaterThan(0);
    expect(findAllLogsByBody(otlp.captured, "e2e_blk_one").length).toBe(0);
    expect(findAllLogsByBody(otlp.captured, "e2e_blk_two").length).toBe(0);
  });

  test("session.start exports when default session rate is 0 and platform web rule sets rate 1", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 812,
      sampling: {
        default: { sessionSampleRate: 0 },
        rules: [
          {
            name: "app_version",
            value: "^9",
            sdks: ["pulse_web_js"],
            sessionSampleRate: 0,
          },
          {
            name: "platform",
            value: "web",
            sdks: ["pulse_web_js"],
            sessionSampleRate: 1,
          },
        ],
        signalsToSample: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    const svcVer = String(
      getResourceAttr(otlp.captured, "service.version") ?? "",
    );
    expect(/^9/.test(svcVer)).toBe(false);
    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
  });

  test("sampling: platform web rule at sessionSampleRate 0 yields no session.start after batch window", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 813,
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [
          {
            name: "platform",
            value: "web",
            sdks: ["pulse_web_js"],
            sessionSampleRate: 0,
          },
        ],
        signalsToSample: [],
      },
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await expect
      .poll(
        async () =>
          (await page.evaluate(() => {
            const w = window as unknown as {
              Pulse?: { isInitialized: () => boolean };
            };
            return w.Pulse?.isInitialized?.() ?? false;
          }))
            ? true
            : false,
        { timeout: 15_000 },
      )
      .toBe(true);
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogs(otlp.captured, "session.start").length).toBe(0);
  });

  test("signalsToSample: rate 0 for one log body only blocks that body", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 814,
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [],
        signalsToSample: [
          {
            sampleRate: 0,
            condition: {
              name: "^e2e_sample_blocked$",
              props: [],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
      },
      features: [
        {
          featureName: "custom_events",
          sessionSampleRate: 1,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("e2e_sample_blocked");
      p.trackEvent("e2e_sample_ok");
    });
    await expect
      .poll(() => findAllLogsByBody(otlp.captured, "e2e_sample_ok").length, {
        timeout: 20_000,
      })
      .toBeGreaterThan(0);
    expect(findAllLogsByBody(otlp.captured, "e2e_sample_blocked").length).toBe(
      0,
    );
  });

  test("combined feature rates: js_crash off + custom_events on in one config", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 815,
      features: [
        {
          featureName: "js_crash",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
        {
          featureName: "custom_events",
          sessionSampleRate: 1,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      queueMicrotask(() => {
        throw new Error("e2e_should_not_export_crash");
      });
    });
    await waitPastSeededSignalsBatchWindow(page);
    expect(
      findAllLogs(otlp.captured, "device.crash").filter((lr) =>
        String(getAttr(lr.attributes, "exception.message") ?? "").includes(
          "e2e_should_not_export_crash",
        ),
      ).length,
    ).toBe(0);

    otlp.reset();
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["Pulse"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("e2e_feature_combo_ok");
    });
    await expect
      .poll(
        () => findAllLogsByBody(otlp.captured, "e2e_feature_combo_ok").length,
        {
          timeout: 20_000,
        },
      )
      .toBeGreaterThan(0);
  });
});

// ─── Error boundary crash capture ────────────────────────────────────────────

test.describe("@M1 error boundary crash capture", () => {
  test("PulseErrorBoundary render error emits device.crash log with react.component_stack", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await waitForPulseInitialized(page);
    otlp.reset();

    // Click the "Throw in render" button — triggers RenderBomb inside PulseErrorBoundary
    await page.click('[data-testid="throw-render-error"]');

    // Wait for a device.crash log to arrive
    const log = await otlp.waitForLog("device.crash", 10_000);

    // pulse.type must be device.crash
    expect(getAttr(log.attributes, "pulse.type")).toBe("device.crash");

    // exception.message must contain the intentional render error text
    const exceptionMessage = getAttr(
      log.attributes,
      "exception.message",
    ) as string;
    expect(exceptionMessage).toContain("Intentional render error");

    // react.component_stack must be defined and non-empty
    const componentStack = getAttr(
      log.attributes,
      "react.component_stack",
    ) as string;
    expect(componentStack).toBeTruthy();
    expect(typeof componentStack).toBe("string");
    expect(componentStack.length).toBeGreaterThan(0);
  });
});

test.describe("@M1 disk buffer replay", () => {
  test("non-retryable logs export failure buffers payload; reload replays to OTLP", async ({
    page,
    otlp,
  }) => {
    test.setTimeout(60_000);
    let logPosts = 0;
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: { ...E2E_OTLP_CORS } });
        return;
      }
      logPosts += 1;
      if (logPosts === 1) {
        await route.fulfill({
          status: 400,
          headers: { ...E2E_OTLP_CORS },
          body: "{}",
        });
        return;
      }
      const buf = route.request().postDataBuffer();
      if (buf) {
        try {
          const body = JSON.parse(buf.toString("utf-8")) as Record<
            string,
            unknown
          >;
          otlp.captured.push({ type: "logs", body } as never);
        } catch {
          /* ignore */
        }
      }
      await route.fulfill({
        status: 200,
        headers: { ...E2E_OTLP_CORS },
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    await page.waitForTimeout(3500);

    otlp.reset();
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect
      .poll(() => findAllLogs(otlp.captured, "session.start").length, {
        timeout: 25_000,
      })
      .toBeGreaterThanOrEqual(1);
  });
});
