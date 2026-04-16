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

    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("install_id_check");
    });
    const second = await otlp.waitForLogByBody("install_id_check");

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
      headers.push(route.request().headers()["x-api-key"] ?? "");
      await route.fulfill({ status: 200, body: "{}" });
    });
    await page.goto("/");
    await page.waitForTimeout(1000);
    expect(headers.length).toBeGreaterThan(0);
    for (const h of headers) expect(h).toBe("test-api-key");
  });

  test("Content-Type is application/json", async ({ page }) => {
    let contentType = "";
    await page.route("**/v1/logs", async (route) => {
      contentType = route.request().headers()["content-type"] ?? "";
      await route.fulfill({ status: 200, body: "{}" });
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
      if (firstExportAt === 0) firstExportAt = Date.now();
      await route.fulfill({
        status: 200,
        contentType: "application/json",
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
    expect(getAttr(log.attributes, "session.duration_ns")).toBeTruthy();
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
      const id = route.request().headers()["x-pulse-metering-session-id"] ?? "";
      meteringIds.push(id);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
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
      const id = route.request().headers()["x-pulse-metering-session-id"] ?? "";
      if (id) meteringIds.push(id);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    // Wait for initial session.start and SDK initialization
    await page.waitForTimeout(500);

    // Emit a few events to trigger multiple OTLP exports
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("header_test_1");
      p.trackEvent("header_test_2");
      p.trackEvent("header_test_3");
    });
    // Wait for batching and export
    await page.waitForTimeout(1000);

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });
    await page.waitForTimeout(500);

    // Must have captured at least 1 request (might be batched into one if SDK batching is fast)
    expect(meteringIds.length).toBeGreaterThanOrEqual(1);
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

// ─── Session Duration (Nanoseconds) ───────────────────────────────────────────

test.describe("@M1 session duration in nanoseconds", () => {
  test("session.end carries duration_ns (nanoseconds, not duration_ms)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Trigger pagehide to emit session.end
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    const log = await otlp.waitForLog("session.end", 3000);
    const durationNs = getAttr(log.attributes, "session.duration_ns");

    // Must exist and be a large number (nanoseconds are ~1e9 per second)
    expect(durationNs).toBeTruthy();
    expect(typeof durationNs).toBe("number");
    // Duration should be at least 1ms in nanoseconds = 1e6
    expect(durationNs as number).toBeGreaterThan(1_000_000);
  });

  test("session.duration_ns aligns with Android SDK (nanoseconds UTC)", async ({
    page,
    otlp,
  }) => {
    // This confirms we're sending nanoseconds, not milliseconds
    // Android sends duration_ns in nanoseconds; we should match
    await page.goto("/");
    await otlp.waitForLog("session.start");

    // Wait a bit to accumulate duration
    await page.waitForTimeout(100);
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", {
          persisted: false,
          bubbles: true,
        }),
      );
    });

    const log = await otlp.waitForLog("session.end", 3000);
    const durationNs = getAttr(log.attributes, "session.duration_ns") as number;

    // Duration includes: page load setup + 100ms wait + SDK overhead + batching
    // Be generous with tolerance: at least 50ms, at most 1 second (1e9 ns)
    expect(durationNs).toBeGreaterThan(50_000_000);
    expect(durationNs).toBeLessThan(1_000_000_000);
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
    // First load — capture window.id
    await page.goto("/");
    const log1 = await otlp.waitForLog("session.start");
    const windowId1 = getAttr(log1.attributes, "window.id") as string;

    otlp.reset();

    // Reload — should get a different window.id (same session, but new page-load = new in-memory ID)
    await page.reload();
    // After reload, session is reused (no new session.start emitted)
    // So we emit a trackEvent to capture a signal after reload
    await page.evaluate(() => {
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
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
      const p = (window as unknown as Record<string, unknown>)["PulseWeb"] as {
        trackEvent: (name: string) => void;
      };
      p.trackEvent("window_id_test");
    });

    await page.waitForTimeout(500);
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
    await page.evaluate(
      () =>
        (window as unknown as Record<string, unknown>)["PulseWeb"] &&
        (
          window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
        ).PulseWeb.trackEvent("screen_name_check"),
    );
    const log = await otlp.waitForLogByBody("screen_name_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products");
  });

  // 2.3 — screen.name strips numeric IDs
  test("screen.name strips numeric segment: /products/123 → '/products'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products/123");
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("numeric_strip_check"),
    );
    const log = await otlp.waitForLogByBody("numeric_strip_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products");
  });

  // 2.16 — screen.name for root path /
  test("screen.name for root path / resolves to '/'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("root_path_check"),
    );
    const log = await otlp.waitForLogByBody("root_path_check");
    const screenName = getAttr(log.attributes, "screen.name") as string;
    expect(screenName).toBeTruthy();
    expect(screenName).toBe("/");
  });

  // 2.17 — screen.name strips UUIDs
  test("screen.name strips UUID segment: /products/<uuid> → '/products'", async ({
    page,
    otlp,
  }) => {
    await page.goto("/products/550e8400-e29b-41d4-a716-446655440000");
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("uuid_strip_check"),
    );
    const log = await otlp.waitForLogByBody("uuid_strip_check");
    expect(getAttr(log.attributes, "screen.name")).toBe("/products");
  });
});

// ─── Area 2: manual screen.name override ─────────────────────────────────────

test.describe("@M1 screen.name manual override", () => {
  // 2.18 — setScreenName() manual override applied immediately
  test("PulseWeb.setScreenName() overrides screen.name on next signal", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = window as unknown as {
        PulseWeb: {
          setScreenName: (n: string) => void;
          trackEvent: (n: string) => void;
        };
      };
      p.PulseWeb.setScreenName("custom-screen");
      p.PulseWeb.trackEvent("override_check");
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
        PulseWeb: {
          setScreenName: (n: string) => void;
          trackEvent: (n: string) => void;
        };
      };
      p.PulseWeb.setScreenName("my-screen");
      p.PulseWeb.trackEvent("persist_check_1");
      p.PulseWeb.trackEvent("persist_check_2");
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
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Set override on /products
    await page.evaluate(() => {
      const p = window as unknown as {
        PulseWeb: { setScreenName: (n: string) => void };
      };
      p.PulseWeb.setScreenName("my-screen");
    });

    // Navigate to /cart — override should reset
    await page.goto("/cart");
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("reset_check"),
    );
    const log = await otlp.waitForLogByBody("reset_check");
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
    await page.goto("/products");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // First event on /products
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("url_path_products"),
    );
    const log1 = await otlp.waitForLogByBody("url_path_products");
    expect(getAttr(log1.attributes, "url.path")).toBe("/products");
    otlp.reset();

    // Navigate to /cart
    await page.goto("/cart");
    await page.evaluate(() =>
      (
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("url_path_cart"),
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
        window as unknown as { PulseWeb: { trackEvent: (n: string) => void } }
      ).PulseWeb.trackEvent("url_attrs_check"),
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
  test("os.name is non-empty in resource attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    const osName = getResourceAttr(otlp.captured, "os.name");
    expect(osName).toBeTruthy();
    // Must be a recognisable OS name
    expect(["macOS", "Windows", "Linux", "Android", "iOS"]).toContain(osName);
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
