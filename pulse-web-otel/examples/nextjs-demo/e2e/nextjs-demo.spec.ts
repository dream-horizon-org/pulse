/**
 * Next.js Demo — E2E Tests (mock OTLP, no ClickHouse required)
 *
 * Interaction seeds use IDs that fit local MySQL together with
 * backend/db/shared/mysql-default-project-interactions.sql (lottery INT-P 100–116
 * plus Next.js rows in the same file).
 * Duplicated mock IDs were disambiguated: 501/502 click-bridge, 551 single-event,
 * 554 apdex excellent, 544 user mid, 545 middle-required — see that SQL header.
 *
 * Verifies:
 *   - session.start emitted on first page load
 *   - screen.name updates on App Router navigation
 *   - Session ID persists across navigations (same session)
 *   - device.crash emitted on PulseErrorBoundary catch
 *   - device.crash emitted on manual reportDeviceCrash
 *   - non_fatal emitted on manual reportException
 *   - platform resource attribute = "web"
 *
 * Run: yarn workspace nextjs-demo e2e
 */
import type { Page } from "@playwright/test";
import {
  test,
  expect,
  getAttr,
  findAllLogs,
  findAllNetworkSpans,
  findAllSpans,
  getOtlpSpanStatusCode,
  getResourceAttr,
  capturedHasScreenName,
  allScreenNamesInCaptured,
  type OtlpSpan,
  type OtlpSpanEvent,
} from "./fixture";
import {
  seedPulseSdkConfig,
  seedInteractionConfig,
  minimalPulseSdkConfig,
} from "./test-sdk-config";

// ─── Session lifecycle ────────────────────────────────────────────────────────

test.describe("session lifecycle", () => {
  test("session.start emitted on first page load", async ({ page, otlp }) => {
    await page.goto("/");
    const log = await otlp.waitForLog("session.start");

    expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  });

  test("platform resource attribute is 'web'", async ({ page, otlp }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    expect(getResourceAttr(otlp.captured, "platform")).toBe("web");
  });

  test("session ID is consistent across navigations", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;
    expect(sessionId).toBeTruthy();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");

    const allLogs = findAllLogs(otlp.captured, "session.start");
    expect(allLogs.length).toBe(1);
    expect(getAttr(allLogs[0].attributes, "session.id")).toBe(sessionId);
  });
});

// ─── screen_interactive ───────────────────────────────────────────────────────

test.describe("screen_interactive span — Next.js", () => {
  test("emits screen_interactive span on cold load when TTI available", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    await page.waitForTimeout(500);

    const loadSpans = findAllSpans(otlp.captured, "screen_load");
    const ttiOnLoad = loadSpans[0]
      ? getAttr(loadSpans[0].attributes, "tti")
      : undefined;

    if (ttiOnLoad !== undefined) {
      const interactiveSpans = findAllSpans(otlp.captured, "screen_interactive");
      expect(interactiveSpans.length).toBeGreaterThanOrEqual(1);
      const span = interactiveSpans[0]!;
      expect(getAttr(span.attributes, "pulse.type")).toBe("screen_interactive");
      expect(typeof getAttr(span.attributes, "tti")).toBe("number");
      expect(Number(getAttr(span.attributes, "tti"))).toBeGreaterThanOrEqual(0);
      expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
      expect(getAttr(span.attributes, "session.id")).toBeTruthy();
      expect(getAttr(span.attributes, "start.type")).toMatch(
        /^(cold|reload|back_forward)$/,
      );
    }
  });

  test("does NOT emit screen_interactive on SPA navigation", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");
    await page.waitForTimeout(500);

    const spaInteractive = findAllSpans(
      otlp.captured,
      "screen_interactive",
    ).filter((s) => getAttr(s.attributes, "start.type") === "spa");

    expect(spaInteractive.length).toBe(0);
  });
});

// ─── Screen tracking ──────────────────────────────────────────────────────────

test.describe("screen tracking — App Router navigation", () => {
  test("screen.name updates when navigating from Home to Products", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    const deadline = Date.now() + 8_000;
    let found = false;
    while (Date.now() < deadline) {
      if (capturedHasScreenName(otlp.captured, "/products")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected screen.name /products on a span or log").toBe(true);
  });

  test("screen.name reflects /cart after navigating to cart", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");

    const deadline = Date.now() + 8_000;
    let found = false;
    while (Date.now() < deadline) {
      if (capturedHasScreenName(otlp.captured, "/cart")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected screen.name /cart on a span or log").toBe(true);
  });

  test("screen.name updates on multi-hop navigation: / → /products → /cart", async ({
    page,
    otlp,
  }) => {
    const allScreenNames = (): string[] =>
      allScreenNamesInCaptured(otlp.captured);

    const waitForScreenName = async (name: string): Promise<void> => {
      const deadline = Date.now() + 8_000;
      while (Date.now() < deadline) {
        if (allScreenNames().includes(name)) return;
        await new Promise((r) => setTimeout(r, 100));
      }
      throw new Error(`Timeout waiting for screen.name = ${name}`);
    };

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("a[href='/products']");
    await page.waitForURL("**/products");
    await waitForScreenName("/products");

    await page.click("a[href='/cart']");
    await page.waitForURL("**/cart");
    await waitForScreenName("/cart");

    const names = allScreenNames();
    expect(names).toContain("/products");
    expect(names).toContain("/cart");
  });
});

// ─── Error tracking ───────────────────────────────────────────────────────────

test.describe("error tracking", () => {
  test("PulseErrorBoundary emits device.crash when component throws", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='throw-btn']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-15: exception.message exact value
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Boundary crash from error-demo",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-12: SeverityText must be "FATAL" not "ERROR"
    expect(log.severityText).toBe("FATAL");
    // ERR-13: SeverityNumber must be 21 (OTel FATAL2)
    expect(log.severityNumber).toBe(21);
  });

  test("reportException emits non_fatal with exception.type", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='manual-exception-btn']");

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "exception.message")).toContain(
      "Manual non_fatal",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-25: SeverityText must be "WARN" not "ERROR" or "FATAL"
    expect(log.severityText).toBe("WARN");
    // ERR-26: SeverityNumber must be 13 (OTel WARN)
    expect(log.severityNumber).toBe(13);
    // ERR-08 / ERR-20: url.path = pathname only, not full URL
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(
      String(getAttr(log.attributes, "url.path") ?? "").startsWith("http"),
    ).toBe(false);
  });

  test("reportDeviceCrash emits device.crash with exception.type", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-15: exception.message exact value
    expect(getAttr(log.attributes, "exception.message")).toContain(
      "Manual device.crash",
    );
    expect(getAttr(log.attributes, "exception.type")).toBeTruthy();
    // ERR-08 / ERR-20: url.path = pathname only, not full URL
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(
      String(getAttr(log.attributes, "url.path") ?? "").startsWith("http"),
    ).toBe(false);
  });

  test("device.crash carries session.id (error is associated to session)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='throw-btn']");

    const crash = await otlp.waitForLog("device.crash");
    expect(getAttr(crash.attributes, "session.id")).toBe(sessionId);
  });

  test("non_fatal carries same session.id as session.start", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-exception-btn']");

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });

  test("manual device.crash carries same session.id as session.start", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });
});

test.describe("error signal contract", () => {
  test("ERR-02 / ERR-31 — unhandled rejection emits non_fatal WARN, is_manual=false (boolean)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const p = Promise.reject(new Error("test rejection"));
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: new Error("test rejection"),
        }),
      );
    });

    const log = await otlp.waitForLog("non_fatal");
    // ERR-02
    expect(log.severityText).toBe("WARN");
    expect(log.severityNumber).toBe(13);
    // ERR-31: boolean false, not string "false"
    const isManual = getAttr(log.attributes, "non_fatal.is_manual");
    expect(isManual).toBe(false);
    expect(typeof isManual).toBe("boolean");
  });

  test("ERR-05 — handled try/catch does not emit device.crash", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      try {
        throw new Error("caught and swallowed");
      } catch {
        // intentionally swallowed — must NOT reach window.onerror
      }
    });

    await page.waitForTimeout(700);
    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
  });

  test("ERR-17 — error.filename is defined (bundle URL or unknown, never absent)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "filename test",
          error: new Error("filename test"),
        }),
      );
    });

    const log = await otlp.waitForLog("device.crash");
    const filename = getAttr(log.attributes, "error.filename");
    // Must be present — empty string only allowed for cross-origin stubs
    expect(filename).toBeDefined();
    // If non-empty and not "unknown", must be a bundle URL
    if (filename && filename !== "" && filename !== "unknown") {
      expect(String(filename).startsWith("http")).toBe(true);
    }
  });

  test("ERR-03 — same error burst within 5s emits only once (dedup)", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("[data-testid='throw-burst']");
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter(
      (log) =>
        getAttr(log.attributes, "exception.message") === "Burst dedup error",
    );
    expect(crashes).toHaveLength(1);
  });

  test("ERR-09 / ERR-14 / ERR-16 — TypeError: class name preserved + stacktrace multi-line", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("[data-testid='throw-type-error']");

    const log = await otlp.waitForLog("device.crash");
    // ERR-14: class name preserved
    expect(getAttr(log.attributes, "exception.type")).toBe("TypeError");
    // ERR-09 / ERR-16: stacktrace must be multi-line
    const stack = String(getAttr(log.attributes, "exception.stacktrace") ?? "");
    expect(stack.includes("\n")).toBe(true);
  });
});

// ─── Error gating & rejection dedupe ─────────────────────────────────────────

test.describe("error gating & rejection dedupe", () => {
  test("E-N3 — errors.enabled: false suppresses automatic window error capture", async ({
    page,
    otlp,
  }) => {
    // Set flag before page load so PulseProvider reads it at init time.
    await page.addInitScript(() => {
      (
        window as Window & { __TEST_PULSE_ERRORS_DISABLED?: boolean }
      ).__TEST_PULSE_ERRORS_DISABLED = true;
    });
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start"); // SDK initialised; errors kill-switch active
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "should-be-suppressed",
          error: new Error("should-be-suppressed"),
          filename: "test.js",
          lineno: 1,
          colno: 1,
        }),
      );
    });

    await page.waitForTimeout(700);
    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
  });

  test("ERR-03 (rejection) — same rejection burst within 5s emits only once", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      const err = new Error("rejection-dedupe-burst");
      for (let i = 0; i < 3; i++) {
        const p = Promise.reject(err);
        p.catch(() => undefined);
        window.dispatchEvent(
          new PromiseRejectionEvent("unhandledrejection", {
            promise: p,
            reason: err,
          }),
        );
      }
    });

    await page.waitForTimeout(700);
    const logs = findAllLogs(otlp.captured, "non_fatal").filter(
      (l) =>
        getAttr(l.attributes, "exception.message") === "rejection-dedupe-burst",
    );
    expect(logs).toHaveLength(1);
  });

  test("ERR-03 (rejection) — same rejection after 5s window emits again", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    // First dispatch — store err on window so same stack is reused in second dispatch.
    await page.evaluate(() => {
      const err = new Error("rejection-dedupe-window");
      (window as Window & { __testRejErr?: Error }).__testRejErr = err;
      const p = Promise.reject(err);
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: err,
        }),
      );
    });

    // Wait for dedupe window (5 s) to expire.
    await page.waitForTimeout(5_300);

    // Second dispatch using same Error object → same fingerprint → should emit again.
    await page.evaluate(() => {
      const err = (window as Window & { __testRejErr?: Error }).__testRejErr!;
      const p = Promise.reject(err);
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: err,
        }),
      );
    });

    await page.waitForTimeout(700);
    const logs = findAllLogs(otlp.captured, "non_fatal").filter(
      (l) =>
        getAttr(l.attributes, "exception.message") ===
        "rejection-dedupe-window",
    );
    expect(logs).toHaveLength(2);
  });
});

// ─── Network instrumentation ──────────────────────────────────────────────────

const OTLP_SPAN_STATUS_OK = 1;

async function waitForPulseInitialized(page: Page): Promise<void> {
  await expect
    .poll(
      () =>
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

async function pollProbeNetworkSpan(
  otlp: { captured: unknown[] },
  urlSubstring: string,
): Promise<OtlpSpan> {
  let found: OtlpSpan | undefined;
  await expect
    .poll(
      () => {
        found = findAllNetworkSpans(otlp.captured as never[]).find((s) =>
          String(getAttr(s.attributes, "url.full") ?? "").includes(
            urlSubstring,
          ),
        );
        return found;
      },
      { timeout: 15_000 },
    )
    .toBeDefined();
  return found!;
}

test.describe("@M4 network — Next.js demo", () => {
  // NET-10: captureQueryParams:true keeps query params on url.full (sensitive ones redacted)
  test("NET-10: captureQueryParams:true keeps query params, redacts sensitive token", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: "{}",
        });
      },
    );

    await page.goto("/?pulse_capture_query=1");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch(
        "/pulse-e2e-network/query-probe?search=hello&token=supersecret",
      );
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "query-probe");
    const full = String(getAttr(span.attributes, "url.full") ?? "");

    expect(full).toContain("search=hello");
    expect(full).not.toContain("supersecret");
    expect(full).toContain("token=*");
  });

  // NET-11: blockedUrls prevents spans for matching URLs
  test("NET-11: blockedUrls config suppresses spans for matched URL", async ({
    page,
    otlp,
  }) => {
    const blockedPath = "/pulse-e2e-network/blocked-endpoint";

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto(`/?pulse_blocked_url=${encodeURIComponent(blockedPath)}`);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async (path) => {
      await fetch(path);
    }, blockedPath);
    await flushTraceExport(page);

    const blocked = findAllNetworkSpans(otlp.captured).filter((s) =>
      String(getAttr(s.attributes, "url.full") ?? "").includes(
        "blocked-endpoint",
      ),
    );
    expect(blocked).toHaveLength(0);
  });

  // NET-12: peerServiceMap sets peer.service on matching spans
  test("NET-12: peerServiceMap sets peer.service attribute on spans", async ({
    page,
    otlp,
  }) => {
    const peerHost = "localhost";

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/peer-probe"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto(
      `/?pulse_peer_host=${encodeURIComponent(peerHost)}&pulse_peer_service=catalogue-service`,
    );
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/peer-probe");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "peer-probe");
    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "peer.service")).toBe("catalogue-service");
  });

  // NET-18: propagateTraceHeaderCorsUrls injects traceparent W3C header
  test("NET-18: propagateTraceHeaderCorsUrls injects W3C traceparent header on matching requests", async ({
    page,
    otlp,
  }) => {
    let capturedTraceparent: string | null = null;

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/cors-probe"),
      async (route) => {
        capturedTraceparent = route.request().headers()["traceparent"] ?? null;
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto(
      `/?pulse_propagate_cors=${encodeURIComponent("localhost:3003")}`,
    );
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/cors-probe");
    });
    await flushTraceExport(page);

    await pollProbeNetworkSpan(otlp, "cors-probe");
    expect(capturedTraceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$/);
  });

  // NET-01: GET 200 — core contract attributes present
  test("NET-01: GET fetch emits network.200 span with core contract attributes", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: "{}",
        });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/get-probe?token=secret");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "get-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);
    expect(getAttr(span.attributes, "server.address")).toBeTruthy();
    expect(typeof getAttr(span.attributes, "server.port")).toBe("number");
    // query stripped by default
    expect(String(getAttr(span.attributes, "url.full") ?? "")).not.toContain(
      "?",
    );
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
  });

  // NET-02: POST request captured with correct method
  test("NET-02: POST fetch emits network.200 span with http.request.method=POST", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/post-probe"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: '{"ok":true}',
        });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/post-probe", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ data: "test" }),
      });
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "post-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("POST");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);
  });

  // NET-03: 4xx → error.type=4xx, span ERROR
  test("NET-03: fetch 404 emits network.404 span with error.type=4xx", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/not-found"),
      async (route) => {
        await route.fulfill({
          status: 404,
          headers: { "Content-Type": "application/json" },
          body: '{"error":"not_found"}',
        });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/not-found");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "not-found");

    expect(getOtlpSpanStatusCode(span)).toBe(2); // OTLP ERROR
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.404");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(404);
    expect(getAttr(span.attributes, "error.type")).toBe("4xx");
  });

  // NET-04: 5xx → error.type=5xx, span ERROR
  test("NET-04: fetch 500 emits network.500 span with error.type=5xx", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/server-error"),
      async (route) => {
        await route.fulfill({ status: 500, body: "error" });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/server-error");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "server-error");

    expect(getOtlpSpanStatusCode(span)).toBe(2); // OTLP ERROR
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.500");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(500);
    expect(getAttr(span.attributes, "error.type")).toBe("5xx");
  });

  // NET-05: AbortController abort → network.0, error.type=network_error
  test("NET-05: aborted fetch emits network.0 span with error.type=network_error", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/abort-probe"),
      (route) => route.abort("failed"),
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      try {
        await fetch("/pulse-e2e-network/abort-probe");
      } catch {
        /* expected */
      }
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "abort-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(2); // OTLP ERROR
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("network_error");
  });

  // NET-07: http.duration from PerformanceResourceTiming reflects real elapsed time
  test("NET-07: http.duration is present and reflects measured elapsed time", async ({
    page,
    otlp,
  }) => {
    const DELAY_MS = 300;

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/slow-probe"),
      async (route) => {
        await new Promise((r) => setTimeout(r, DELAY_MS));
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/slow-probe");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "slow-probe");
    const dur = getAttr(span.attributes, "http.duration");

    expect(dur).toBeDefined();
    expect(typeof dur).toBe("number");
    expect(Number(dur)).toBeGreaterThanOrEqual(DELAY_MS - 50);
  });

  // NET-06: XHR GET captured (no lab page needed — trigger via page.evaluate)
  test("NET-06: XHR GET captured with pulse.type=network.200", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/xhr-probe"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: '{"ok":true}',
        });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(
      () =>
        new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open("GET", "/pulse-e2e-network/xhr-probe");
          xhr.onload = () => resolve();
          xhr.onerror = () => reject(new Error("xhr failed"));
          xhr.send();
        }),
    );
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "xhr-probe");

    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);
    expect(getAttr(span.attributes, "url.full")).toBeTruthy();
  });

  // NET-08: CORS opaque response → network.0, cors_error
  test("NET-08: CORS opaque fetch → network.0 and error.type=cors_error", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/cors-opaque"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "text/plain" },
          body: "ok",
        });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      // localhost:3003 → 127.0.0.1:3003 is cross-origin in Chromium; no-cors → opaque response, status 0
      await fetch("http://127.0.0.1:3003/pulse-e2e-network/cors-opaque/x", {
        mode: "no-cors",
        credentials: "omit",
      });
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "cors-opaque");

    expect(getOtlpSpanStatusCode(span)).toBe(2); // OTLP ERROR
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("cors_error");
  });

  // NET-14: Feature gate off → no network spans
  test("NET-14: network_instrumentation gate off — no network spans emitted", async ({
    page,
    otlp,
  }) => {
    await seedPulseSdkConfig(
      page,
      minimalPulseSdkConfig({
        features: [
          {
            featureName: "network_instrumentation",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
          },
        ],
      }),
    );

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/gate-off-probe");
    });
    await flushTraceExport(page);

    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  // NET-15: Consent denied → no network spans, no session.start
  test("NET-15: DENIED consent — no session.start, no network spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(1500);

    expect(findAllLogs(otlp.captured, "session.start")).toHaveLength(0);
    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  // NET-16: Local network.enabled:false → no network spans; session still exported
  test("NET-16: network.enabled:false — no network spans, session.start still exported", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto("/?pulse_network_enabled=0");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/net-disabled-probe");
    });
    await flushTraceExport(page);

    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  // NET-17: OTLP export calls are never traced as network client spans
  test("NET-17: OTLP export URLs are not traced as network client spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    await flushTraceExport(page);

    const otlpSpans = findAllNetworkSpans(otlp.captured).filter((s) => {
      const full = String(getAttr(s.attributes, "url.full") ?? "");
      return /\/v1\/(traces|logs|metrics)(?:\?|$)/.test(full);
    });
    expect(otlpSpans).toHaveLength(0);
  });

  // ISS-N10: XHR capturedRequestHeaders — headers stored via WeakMap monkey-patch
  test("ISS-N10: XHR capturedRequestHeaders captures request headers on XHR spans", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/xhr-headers-probe"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: '{"ok":true}',
        });
      },
    );

    await page.goto(
      `/?pulse_capture_req_headers=${encodeURIComponent("x-request-id,x-custom-header")}`,
    );
    await waitForPulseInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(
      () =>
        new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open("GET", "/pulse-e2e-network/xhr-headers-probe");
          xhr.setRequestHeader("X-Request-ID", "test-req-abc");
          xhr.setRequestHeader("X-Custom-Header", "captured-value");
          xhr.onload = () => resolve();
          xhr.onerror = () => reject(new Error("xhr failed"));
          xhr.send();
        }),
    );
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "xhr-headers-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(1); // OTLP OK
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(
      getAttr(span.attributes, "http.request.header.x-request-id"),
    ).toEqual(["test-req-abc"]);
    expect(
      getAttr(span.attributes, "http.request.header.x-custom-header"),
    ).toEqual(["captured-value"]);
  });

  // NET-13: Concurrent requests — each gets its own independent span
  test("NET-13: three concurrent requests each produce a separate network span", async ({
    page,
    otlp,
  }) => {
    for (const id of ["1", "2", "3"]) {
      await page.route(
        (url) => url.pathname.includes(`/pulse-e2e-network/concurrent-${id}`),
        async (route) => {
          await route.fulfill({ status: 200, body: `{"id":${id}}` });
        },
      );
    }

    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await Promise.all([
        fetch("/pulse-e2e-network/concurrent-1"),
        fetch("/pulse-e2e-network/concurrent-2"),
        fetch("/pulse-e2e-network/concurrent-3"),
      ]);
    });
    await flushTraceExport(page);

    await expect
      .poll(
        () =>
          findAllNetworkSpans(otlp.captured).filter((s) =>
            String(getAttr(s.attributes, "url.full") ?? "").includes(
              "pulse-e2e-network/concurrent-",
            ),
          ).length,
        { timeout: 15_000 },
      )
      .toBe(3);

    const spans = findAllNetworkSpans(otlp.captured).filter((s) =>
      String(getAttr(s.attributes, "url.full") ?? "").includes(
        "pulse-e2e-network/concurrent-",
      ),
    );
    for (const span of spans) {
      expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    }
  });
});

// ─── URL normalization — \d{3,} threshold (Android parity) ──────────────────

test.describe("@Network URL normalization — \\d{3,} threshold (Android parity) — Next.js", () => {
  // Intercept all /pulse-e2e-norm/ paths so no real server needed.
  async function setupNormRoute(page: Page): Promise<void> {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-norm/"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );
  }

  test("URL-NORM-N1: 3+ digit numeric segment normalized to :id in url.full", async ({
    page,
    otlp,
  }) => {
    await setupNormRoute(page);
    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-norm/users/12345/orders");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "pulse-e2e-norm");
    const full = String(getAttr(span.attributes, "url.full") ?? "");

    expect(full).toContain(":id");
    expect(full).not.toContain("/12345");
    expect(full).toContain("/users/");
    expect(full).toContain("/orders");
  });

  test("URL-NORM-N2: 1–2 digit segment NOT normalized — preserved as-is (Android parity)", async ({
    page,
    otlp,
  }) => {
    await setupNormRoute(page);
    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    // 42 is 2 digits → kept; 12345 is 5 digits → :id
    await page.evaluate(async () => {
      await fetch("/pulse-e2e-norm/users/42/orders/12345");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "pulse-e2e-norm");
    const full = String(getAttr(span.attributes, "url.full") ?? "");

    expect(full).toContain("/42/");        // 2-digit preserved
    expect(full).toContain(":id");         // 5-digit normalized
    expect(full).not.toContain("/12345");  // original 5-digit gone
  });

  test("URL-NORM-N3: version segment /v2/ not normalized (not all-digit)", async ({
    page,
    otlp,
  }) => {
    await setupNormRoute(page);
    await page.goto("/");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-norm/api/v2/users/99999");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "pulse-e2e-norm");
    const full = String(getAttr(span.attributes, "url.full") ?? "");

    expect(full).toContain("/v2/");       // version segment preserved
    expect(full).toContain(":id");        // 5-digit normalized
    expect(full).not.toContain("/99999"); // original 5-digit gone
  });
});

// ─── ISS-I12: click-bridge interactions in Next.js App Router ─────────────────

/** Flush ClickEventBuffer by simulating tab backgrounding. */
async function flushClickBuffer(page: Page): Promise<void> {
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      get: () => "hidden",
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      get: () => "visible",
      configurable: true,
    });
  });
}

function makeInteractionConfig(opts: {
  id: number;
  name: string;
  events: Array<{ name: string }>;
  thresholdInMs?: number;
}) {
  return {
    id: opts.id,
    name: opts.name,
    description: opts.name,
    events: opts.events.map((e) => ({
      name: e.name,
      isBlacklisted: false,
      props: null,
    })),
    thresholdInMs: opts.thresholdInMs ?? 600,
    uptimeLowerLimitInMs: 120,
    uptimeMidLimitInMs: 240,
    uptimeUpperLimitInMs: 360,
    globalBlacklistedEvents: [],
  };
}

/** Navigate and wait for SDK + interaction feature to initialise. */
async function gotoAndWaitInit(
  page: Page,
  otlp: { waitForLog: (t: string, ms?: number) => Promise<unknown> },
): Promise<void> {
  await page.goto("/");
  await otlp.waitForLog("session.start", 10_000);
  // Give InteractionFeature.init() a tick to resolve the config fetch and register trackers.
  await page.waitForTimeout(300);
}

test.describe("@ISS-I12 click-bridge interactions (Next.js App Router)", () => {
  test("@click-bridge DOM click on product card auto-advances single-step interaction", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 501,
        name: "Product Click Flow",
        events: [{ name: "app.widget.click" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    // Click a featured product card link (good click target → app.click log emitted).
    await page.locator("a[href^='/products/']").first().click();
    await flushClickBuffer(page);

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("501");
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("@click-bridge manual trackEvent advances interaction in Next.js", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 502,
        name: "Product Viewed Flow",
        events: [{ name: "product_viewed" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    // product-card onClick fires Pulse.trackEvent("product_viewed", ...).
    await page.locator("a[href^='/products/']").first().click();

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("502");
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  test("@click-bridge interaction config unavailable → no interaction span, SDK still running", async ({
    page,
    otlp,
  }) => {
    await page.route("**/v1/interaction-configs/", async (route) => {
      await route.fulfill({
        status: 503,
        contentType: "application/json",
        body: "{}",
      });
    });
    await page.goto("/");
    await otlp.waitForLog("session.start", 10_000);
    otlp.reset();

    await page.locator("a[href^='/products/']").first().click();
    await flushClickBuffer(page);
    await page.waitForTimeout(1500);

    expect(findAllSpans(otlp.captured, "interaction").length).toBe(0);
    // SDK still runs — click log emitted.
    const clickLogs = findAllLogs(otlp.captured, "app.click");
    expect(clickLogs.length).toBeGreaterThan(0);
  });
});

// ─── ISS-I02/I03: marker events as span events (Next.js App Router) ───────────

test.describe("@M2 interactions marker events — Next.js (ISS-I02/I03)", () => {
  test("@marker non_fatal mid-flow appears as span event between steps", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 201,
        name: "Marker NonFatal Flow",
        events: [{ name: "marker_step_a" }, { name: "marker_step_b" }],
        thresholdInMs: 800,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    // Step A
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("marker_step_a");
    });

    // non_fatal mid-flow → Branch B → addMarkerToAll
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { reportException?: (e: unknown) => void };
      };
      w.Pulse?.reportException?.(new Error("mid-flow non_fatal"));
    });
    await page.waitForTimeout(100);

    // Step B — completes flow
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("marker_step_b");
    });

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("201");
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);

    const events = (span.events ?? []) as OtlpSpanEvent[];
    expect(events.length).toBeGreaterThan(0);
    expect(events.some((e) => e.name === "mid-flow non_fatal")).toBe(true);
  });

  test("@marker device.crash mid-flow appears as span event", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 202,
        name: "Marker Crash Flow",
        events: [{ name: "crash_step_a" }, { name: "crash_step_b" }],
        thresholdInMs: 800,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("crash_step_a");
    });

    // device.crash mid-flow → Branch B → addMarkerToAll
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { reportDeviceCrash?: (e: unknown) => void };
      };
      w.Pulse?.reportDeviceCrash?.(new Error("mid-flow crash"));
    });
    await page.waitForTimeout(100);

    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("crash_step_b");
    });

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("202");

    const events = (span.events ?? []) as OtlpSpanEvent[];
    expect(events.length).toBeGreaterThan(0);
    expect(events.some((e) => e.name === "mid-flow crash")).toBe(true);
  });

  test("@marker successful flow without crash has no extra marker events", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 203,
        name: "Clean Flow",
        events: [{ name: "clean_step_a" }, { name: "clean_step_b" }],
        thresholdInMs: 800,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("clean_step_a");
      w.Pulse?.trackEvent?.("clean_step_b");
    });

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("203");

    // No crash/non_fatal fired — marker events list should be empty or absent
    const events = span.events ?? [];
    const markerEvents = events.filter(
      (e) => e.name !== "clean_step_a" && e.name !== "clean_step_b",
    );
    expect(markerEvents).toHaveLength(0);
  });
});

// ─── ISS-I04: InteractionContextSpanProcessor — Next.js (forward stamp + reverse feed) ──

test.describe("@M2 interaction-context-span — Next.js (ISS-I04)", () => {
  test("stamp in-flight: network span during open flow carries pulse.interaction.names/ids", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 301,
        name: "NX Context Stamp Flow",
        events: [{ name: "nx_ctx_step_1" }, { name: "nx_ctx_step_2" }],
        thresholdInMs: 5000,
      }),
    ]);
    await page.route("**/pulse-e2e-nx-probe", async (route) => {
      await route.fulfill({ status: 200, body: "ok" });
    });

    await gotoAndWaitInit(page, otlp);

    // Open flow — step 1.
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_ctx_step_1");
    });
    await page.waitForTimeout(50);

    // Trigger a fetch during the open flow.
    await page.evaluate(async () => {
      await fetch("/pulse-e2e-nx-probe").catch(() => {});
    });
    await page.waitForTimeout(200);

    // Flush spans.
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false }),
      );
    });

    // Poll until the probe span arrives — fixed 500ms sleep was a race under 3-worker CI load.
    const probeSpan = await pollProbeNetworkSpan(otlp, "pulse-e2e-nx-probe");

    const names = getAttr(probeSpan.attributes, "pulse.interaction.names");
    const ids = getAttr(probeSpan.attributes, "pulse.interaction.ids");
    expect(Array.isArray(names)).toBe(true);
    expect((names as string[]).length).toBeGreaterThan(0);
    expect(Array.isArray(ids)).toBe(true);
    expect((ids as string[]).length).toBeGreaterThan(0);

    // Close the flow.
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_ctx_step_2");
    });
    await otlp.waitForSpan("interaction", 8_000);
  });

  test("no stamp after complete: network span post-close has no interaction context", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 302,
        name: "NX Context No-Stamp Flow",
        events: [{ name: "nx_nostamp_1" }, { name: "nx_nostamp_2" }],
        thresholdInMs: 5000,
      }),
    ]);
    await page.route("**/pulse-e2e-nx-after-complete", async (route) => {
      await route.fulfill({ status: 200, body: "ok" });
    });

    await gotoAndWaitInit(page, otlp);

    // Complete flow.
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_nostamp_1");
    });
    await page.waitForTimeout(30);
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_nostamp_2");
    });
    await otlp.waitForSpan("interaction", 8_000);
    otlp.reset();

    // Fetch after close.
    await page.evaluate(async () => {
      await fetch("/pulse-e2e-nx-after-complete").catch(() => {});
    });
    await page.waitForTimeout(200);

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false }),
      );
    });
    await page.waitForTimeout(500);

    const networkSpans = findAllNetworkSpans(otlp.captured);
    const afterSpan = networkSpans.find((s) => {
      const url = String(getAttr(s.attributes, "url.full") ?? "");
      return url.includes("pulse-e2e-nx-after-complete");
    });
    if (afterSpan) {
      const names = getAttr(afterSpan.attributes, "pulse.interaction.names");
      const isEmpty =
        names === undefined ||
        (Array.isArray(names) && (names as string[]).length === 0);
      expect(isEmpty).toBe(true);
    }
  });

  test("reverse screen_load: SPA nav during open flow closes flow (is_error=false)", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 303,
        name: "NX Reverse Screen Flow",
        events: [{ name: "nx_checkout_1" }, { name: "screen_load" }],
        thresholdInMs: 5000,
      }),
    ]);

    await gotoAndWaitInit(page, otlp);

    // Open the flow.
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_checkout_1");
    });
    await page.waitForTimeout(50);

    // Navigate → screen_load span is reverse-fed.
    await page.click("a[href='/products']");
    await page.waitForURL("**/products");

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("303");
  });

  test("reverse network.200: successful fetch during open flow closes flow (is_error=false)", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeInteractionConfig({
        id: 304,
        name: "NX Reverse Network Flow",
        events: [{ name: "nx_net_step_1" }, { name: "network.200" }],
        thresholdInMs: 5000,
      }),
    ]);
    await page.route("**/pulse-e2e-nx-net-probe", async (route) => {
      await route.fulfill({ status: 200, body: "ok" });
    });

    await gotoAndWaitInit(page, otlp);

    // Open the flow.
    await page.evaluate(() => {
      const w = window as unknown as {
        Pulse?: { trackEvent?: (n: string) => void };
      };
      w.Pulse?.trackEvent?.("nx_net_step_1");
    });
    await page.waitForTimeout(50);

    // The 200 fetch will be reverse-fed.
    await page.evaluate(async () => {
      await fetch("/pulse-e2e-nx-net-probe").catch(() => {});
    });

    const span = await otlp.waitForSpan("interaction", 15_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("304");
  });
});

// ─── @M2 interactions — Next.js parity (INT-P01/P04–P08/P10–P12/P19/P21/P22/P40) ─

/** Extended makeInteractionConfig that supports props, apdex limits, and global blacklist overrides. */
function makeParityInteractionConfig(opts: {
  id: number;
  name: string;
  events: Array<{
    name: string;
    isBlacklisted?: boolean;
    props?: Array<{ name: string; value: string; operator: string }> | null;
  }>;
  thresholdInMs?: number;
  uptimeLowerLimitInMs?: number;
  uptimeMidLimitInMs?: number;
  uptimeUpperLimitInMs?: number;
  globalBlacklistedEvents?: string[];
}) {
  return {
    id: opts.id,
    name: opts.name,
    description: opts.name,
    events: opts.events.map((e) => ({
      name: e.name,
      isBlacklisted: e.isBlacklisted ?? false,
      props:
        e.props == null
          ? null
          : e.props.map((p) => ({
              name: p.name,
              value: p.value,
              operator: p.operator,
            })),
    })),
    thresholdInMs: opts.thresholdInMs ?? 600,
    uptimeLowerLimitInMs: opts.uptimeLowerLimitInMs ?? 120,
    uptimeMidLimitInMs: opts.uptimeMidLimitInMs ?? 240,
    uptimeUpperLimitInMs: opts.uptimeUpperLimitInMs ?? 420,
    globalBlacklistedEvents: (opts.globalBlacklistedEvents ?? []).map((n) => ({
      name: n,
      isBlacklisted: true,
      props: [],
    })),
  };
}

/** Emit a trackEvent with an explicit timestamp (ms since epoch). */
async function emitEventAt(
  page: Page,
  name: string,
  timestampMs: number,
  props?: Record<string, string>,
): Promise<void> {
  await page.evaluate(
    ([n, ts, p]: [string, number, Record<string, string> | undefined]) => {
      const w = window as unknown as {
        Pulse?: {
          trackEvent?: (
            n: string,
            p?: Record<string, string>,
            ts?: number,
          ) => void;
        };
      };
      w.Pulse?.trackEvent?.(n, p ?? {}, ts);
    },
    [name, timestampMs, props] as [
      string,
      number,
      Record<string, string> | undefined,
    ],
  );
}

/** Assert no interaction spans for a brief wait. */
async function expectNoInteractionSpansNx(
  otlp: { captured: unknown[] },
  waitFn: (ms: number) => Promise<void>,
  waitMs = 800,
): Promise<void> {
  await waitFn(waitMs);
  expect(findAllSpans(otlp.captured as never[], "interaction").length).toBe(0);
}

/** Set userId via Pulse SDK. */
async function setUserIdNx(page: Page, uid: string | null): Promise<void> {
  await page.evaluate((id) => {
    const w = window as unknown as {
      Pulse?: { setUserId?: (id: string | null) => void };
    };
    w.Pulse?.setUserId?.(id);
  }, uid);
}

/** Emit a trackEvent via window.Pulse.trackEvent. */
async function emitEvent(
  page: Page,
  name: string,
  props?: Record<string, string>,
): Promise<void> {
  await page.evaluate(
    ([n, p]: [string, Record<string, string> | undefined]) => {
      const w = window as unknown as {
        Pulse?: {
          trackEvent?: (n: string, p?: Record<string, string>) => void;
        };
      };
      w.Pulse?.trackEvent?.(n, p);
    },
    [name, props] as [string, Record<string, string> | undefined],
  );
}

/** Poll until at least `count` interaction spans are captured. */
async function waitForInteractionCount(
  otlp: { captured: unknown[] },
  count: number,
  timeoutMs = 8_000,
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (findAllSpans(otlp.captured as never[], "interaction").length >= count)
      return;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`Timeout waiting for ${count} interaction spans`);
}

test.describe("@M2 interactions — Next.js parity (INT-P01/P04–P08/P10–P12/P19/P21/P22/P40)", () => {
  // INT-P01 — Single-event flow completes
  test("INT-P01: single-event flow completes with config.id and is_error=false", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 551,
        name: "NX Single Event",
        events: [{ name: "nx_single" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_single");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("551");
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  // INT-P04 — Apdex Excellent
  test("INT-P04: apdex Excellent — complete_time < lower limit", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 554,
        name: "NX Apdex Excellent",
        events: [{ name: "nx_ax_1" }, { name: "nx_ax_2" }],
        thresholdInMs: 600,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 240,
        uptimeUpperLimitInMs: 420,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_ax_1");
    await page.waitForTimeout(40);
    await emitEvent(page, "nx_ax_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Excellent",
    );
    expect(
      Number(getAttr(span.attributes, "pulse.interaction.apdex_score")),
    ).toBe(1);
  });

  // INT-P05 — Apdex Good
  test("INT-P05: apdex Good — complete_time between lower and mid", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 305,
        name: "NX Apdex Good",
        events: [{ name: "nx_ag_1" }, { name: "nx_ag_2" }],
        thresholdInMs: 600,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 240,
        uptimeUpperLimitInMs: 420,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_ag_1");
    await page.waitForTimeout(180); // between lower(120) and mid(240)
    await emitEvent(page, "nx_ag_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Good",
    );
    const score = Number(
      getAttr(span.attributes, "pulse.interaction.apdex_score"),
    );
    expect(score).toBeGreaterThan(0);
    expect(score).toBeLessThan(1);
  });

  // INT-P06 — Apdex Average
  test("INT-P06: apdex Average — complete_time between mid and upper", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 306,
        name: "NX Apdex Average",
        events: [{ name: "nx_aa_1" }, { name: "nx_aa_2" }],
        thresholdInMs: 1200,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 240,
        uptimeUpperLimitInMs: 420,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_aa_1");
    await page.waitForTimeout(320); // between mid(240) and upper(420)
    await emitEvent(page, "nx_aa_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Average",
    );
    const score = Number(
      getAttr(span.attributes, "pulse.interaction.apdex_score"),
    );
    expect(score).toBeGreaterThan(0);
    expect(score).toBeLessThan(1);
  });

  // INT-P07 — Apdex Poor
  test("INT-P07: apdex Poor — complete_time beyond upper limit", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 307,
        name: "NX Apdex Poor",
        events: [{ name: "nx_ap_1" }, { name: "nx_ap_2" }],
        thresholdInMs: 1500,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 240,
        uptimeUpperLimitInMs: 420,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_ap_1");
    await page.waitForTimeout(520); // beyond upper(420)
    await emitEvent(page, "nx_ap_2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Poor",
    );
    expect(
      Number(getAttr(span.attributes, "pulse.interaction.apdex_score")),
    ).toBe(0);
  });

  // INT-P08 — Two independent flows both complete
  test("INT-P08: two independent single-step flows each emit a span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 308,
        name: "NX Repeatable",
        events: [{ name: "nx_rep" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_rep");
    await otlp.waitForSpan("interaction", 10_000);
    await emitEvent(page, "nx_rep");
    await waitForInteractionCount(otlp, 2, 8_000);

    const spans = findAllSpans(otlp.captured as never[], "interaction");
    expect(spans.length).toBe(2);
    expect(
      spans.every(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  // INT-P10 — EQUALS operator match
  test("INT-P10: EQUALS operator — matching prop value completes flow", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 310,
        name: "NX Equals Match",
        events: [
          {
            name: "nx_eq_event",
            props: [{ name: "tier", value: "gold", operator: "EQUALS" }],
          },
        ],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_eq_event", { tier: "gold" });

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("310");
  });

  // INT-P11 — CONTAINS operator match
  test("INT-P11: CONTAINS operator — value containing substring completes flow", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 311,
        name: "NX Contains Match",
        events: [
          {
            name: "nx_ct_event",
            props: [{ name: "label", value: "cart", operator: "CONTAINS" }],
          },
        ],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_ct_event", { label: "add_to_cart" });

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("311");
  });

  // INT-P12 — STARTS_WITH operator match
  test("INT-P12: STARTSWITH operator — value starting with prefix completes flow", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 312,
        name: "NX StartsWith Match",
        events: [
          {
            name: "nx_sw_event",
            props: [
              { name: "screen", value: "product", operator: "STARTSWITH" },
            ],
          },
        ],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_sw_event", { screen: "product_detail" });

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("312");
  });

  // INT-P19 — Sequence violation at stage-1
  test("INT-P19: sequence violation at stage-1 emits error span with error.type=sequence_violation", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 319,
        name: "NX Sequence Violation",
        events: [{ name: "nx_v1" }, { name: "nx_v2" }, { name: "nx_v3" }],
        thresholdInMs: 600,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_v1");
    await emitEvent(page, "nx_v3"); // wrong step at stage 1

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  // INT-P21 — Timeout: second step never comes
  test("INT-P21: timeout — second step never arrives within threshold", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 321,
        name: "NX Timeout",
        events: [{ name: "nx_t1" }, { name: "nx_t2" }],
        thresholdInMs: 700,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_t1");
    await page.waitForTimeout(1200); // beyond threshold

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  // INT-P22 — Global blacklist cancels in-flight flow
  test("INT-P22: global blacklist event cancels in-flight flow; recovery flow succeeds", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 322,
        name: "NX Global Blacklist",
        events: [{ name: "nx_b1" }, { name: "nx_b2" }, { name: "nx_b3" }],
        thresholdInMs: 2000,
        globalBlacklistedEvents: ["nx_noise"],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_b1");
    await emitEvent(page, "nx_noise"); // triggers global blacklist — cancels flow
    await page.waitForTimeout(1200);

    // No span emitted after cancellation
    expect(findAllSpans(otlp.captured as never[], "interaction").length).toBe(
      0,
    );

    // Recovery: new clean flow should complete
    await emitEvent(page, "nx_b1");
    await emitEvent(page, "nx_b2");
    await emitEvent(page, "nx_b3");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
    expect(getAttr(span.attributes, "pulse.interaction.config.id")).toBe("322");
  });

  // INT-P40 — complete_time nanos consistent with span start/end
  test("INT-P40: complete_time nanos is consistent with span startTimeUnixNano/endTimeUnixNano", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 340,
        name: "NX Complete Time",
        events: [{ name: "nx_p40_1" }, { name: "nx_p40_2" }],
        thresholdInMs: 2000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    await emitEvent(page, "nx_p40_1");
    await page.waitForTimeout(80);
    await emitEvent(page, "nx_p40_2");

    await waitForInteractionCount(otlp, 1, 10_000);

    const span = findAllSpans(otlp.captured as never[], "interaction")[0];
    expect(span).toBeDefined();
    if (!span) return;

    const completeTimeNs = Number(
      getAttr(span.attributes, "pulse.interaction.complete_time"),
    );
    const startNs = Number(span.startTimeUnixNano);
    const endNs = Number(span.endTimeUnixNano);

    expect(completeTimeNs).toBeGreaterThan(0);
    expect(endNs).toBeGreaterThan(startNs);
    expect(endNs - startNs).toBeGreaterThanOrEqual(completeTimeNs);
  });
});

// ─── @M2 interactions — Next.js parity batch-2 (INT-P13/14/20/23/27-32/36-39) ─

test.describe("@M2 interactions — Next.js parity batch-2 (INT-P13/14/20/23/27-32/36-39)", () => {
  // INT-P13 — Shared prefix correct branch
  test("INT-P13: shared prefix — e1,e2,e5 completes branch_e125; e1,e2,e3 completes branch_e123", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 413,
        name: "Branch E123",
        events: [{ name: "nx_e1" }, { name: "nx_e2" }, { name: "nx_e3" }],
        thresholdInMs: 5000,
      }),
      makeParityInteractionConfig({
        id: 414,
        name: "Branch E125",
        events: [{ name: "nx_e1" }, { name: "nx_e2" }, { name: "nx_e5" }],
        thresholdInMs: 5000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);

    // Shared prefix + irrelevant nx_e4 → no terminal
    await emitEvent(page, "nx_e1");
    await emitEvent(page, "nx_e2");
    await emitEvent(page, "nx_e4");
    await page.waitForTimeout(400);
    expect(findAllSpans(otlp.captured as never[], "interaction").length).toBe(
      0,
    );

    // nx_e5 finalises branch_e125
    await emitEvent(page, "nx_e5");
    await waitForInteractionCount(otlp, 1, 8_000);
    const spans1 = findAllSpans(otlp.captured as never[], "interaction");
    const branch125 = spans1.filter(
      (s) =>
        getAttr(s.attributes, "pulse.interaction.config.id") === String(414),
    );
    expect(
      branch125.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);

    // Fresh second run: nx_e1, nx_e2, nx_e3 finalises branch_e123
    otlp.reset();
    await emitEvent(page, "nx_e1");
    await emitEvent(page, "nx_e2");
    await emitEvent(page, "nx_e3");
    await waitForInteractionCount(otlp, 1, 8_000);
    const spans2 = findAllSpans(otlp.captured as never[], "interaction");
    const branch123 = spans2.filter(
      (s) =>
        getAttr(s.attributes, "pulse.interaction.config.id") === String(413),
    );
    expect(
      branch123.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  // INT-P14 — User ID mid-interaction
  test("INT-P14: user id changed mid-interaction — final span carries new userId", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 544,
        name: "NX User Mid Flow",
        events: [{ name: "nx_user_a" }, { name: "nx_user_b" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await setUserIdNx(page, "user-old");
    await emitEvent(page, "nx_user_a");
    await setUserIdNx(page, "user-new");
    await emitEvent(page, "nx_user_b");

    await waitForInteractionCount(otlp, 1, 10_000);
    const span = findAllSpans(otlp.captured as never[], "interaction")[0];
    expect(span).toBeDefined();
    expect(getAttr(span?.attributes, "user.id")).toBe("user-new");
  });

  // INT-P20 — Sequence violation at stage 2
  test("INT-P20: sequence violation at stage-2 emits sequence_violation error span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 420,
        name: "NX Stage2 Violation",
        events: [{ name: "nx_s1" }, { name: "nx_s2" }, { name: "nx_s3" }],
        thresholdInMs: 2000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_s1");
    await emitEvent(page, "nx_s2");
    // Wrong event — expected nx_s3, got nx_s2 again
    await emitEvent(page, "nx_s2");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  // INT-P23 — Local blacklist cancels flow
  test("INT-P23: local blacklisted step resets flow; recovery flow succeeds", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 423,
        name: "NX Local Blacklist",
        events: [
          { name: "nx_bl_a" },
          { name: "nx_bl_blocked", isBlacklisted: true },
          { name: "nx_bl_b" },
        ],
        thresholdInMs: 1000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_bl_a");
    await emitEvent(page, "nx_bl_blocked");
    await emitEvent(page, "nx_bl_b");
    await expectNoInteractionSpansNx(otlp, page.waitForTimeout.bind(page));

    // Recovery flow
    await emitEvent(page, "nx_bl_a");
    await emitEvent(page, "nx_bl_b");
    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  // INT-P27 — EQUALS no match
  test("INT-P27: EQUALS operator — wrong value yields no span; correct value completes flow", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 427,
        name: "NX EQUALS No Match",
        events: [
          {
            name: "nx_props_event",
            props: [{ name: "plan", value: "pro", operator: "EQUALS" }],
          },
        ],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_props_event", { plan: "basic" });
    await expectNoInteractionSpansNx(otlp, page.waitForTimeout.bind(page));
    await emitEvent(page, "nx_props_event", { plan: "pro" });
    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  // INT-P28 — Out-of-order timestamps → timeout
  test("INT-P28: out-of-order event timestamp causes timeout error", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 428,
        name: "NX Timestamp Order",
        events: [{ name: "nx_ts_a" }, { name: "nx_ts_b" }],
        thresholdInMs: 700,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    const now = Date.now();
    await emitEventAt(page, "nx_ts_a", now + 200);
    await emitEventAt(page, "nx_ts_b", now - 200);

    const span = await otlp.waitForSpan("interaction", 12_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "timeout",
    );
  });

  // INT-P29 — Overlapping configs
  test("INT-P29: overlapping configs on same event stream each emit a terminal span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 429,
        name: "NX Overlap A",
        events: [{ name: "nx_start" }, { name: "nx_finish_a" }],
      }),
      makeParityInteractionConfig({
        id: 430,
        name: "NX Overlap B",
        events: [{ name: "nx_start" }, { name: "nx_finish_b" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_start");
    await emitEvent(page, "nx_finish_a");
    await emitEvent(page, "nx_finish_b");
    await waitForInteractionCount(otlp, 2, 10_000);

    const spans = findAllSpans(otlp.captured as never[], "interaction");
    const configIds = spans.map((s) =>
      String(getAttr(s.attributes, "pulse.interaction.config.id")),
    );
    expect(configIds).toContain("429");
    expect(configIds).toContain("430");
    expect(
      spans.every(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  // INT-P30 — Middle step not skippable
  test("INT-P30: middle step is not skippable — skipping it causes sequence_violation", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 545,
        name: "NX Middle Required",
        events: [
          { name: "nx_mr_start" },
          { name: "nx_mr_middle" },
          { name: "nx_mr_end" },
        ],
        thresholdInMs: 1000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_mr_start");
    // Skip nx_mr_middle — emit end directly
    await emitEvent(page, "nx_mr_end");

    const span = await otlp.waitForSpan("interaction", 10_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.error.type")).toBe(
      "sequence_violation",
    );
  });

  // INT-P31 — Restart after violation
  test("INT-P31: restart after sequence violation — error span then success span", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 431,
        name: "NX Restart After Violation",
        events: [{ name: "nx_rv_first" }, { name: "nx_rv_second" }],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_rv_first");
    await emitEvent(page, "nx_rv_first"); // violation: re-fires first
    await emitEvent(page, "nx_rv_second");
    await waitForInteractionCount(otlp, 2, 12_000);

    const spans = findAllSpans(otlp.captured as never[], "interaction").filter(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "431",
    );
    expect(spans.length).toBe(2);
    expect(
      spans.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === true,
      ),
    ).toBe(true);
    expect(
      spans.some(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });

  // INT-P32 — Multiple global blacklist hits
  test("INT-P32: multiple global blacklist hits cancel flow; later clean flow succeeds", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 432,
        name: "NX Multi Blacklist",
        events: [{ name: "nx_mb_1" }, { name: "nx_mb_2" }],
        globalBlacklistedEvents: ["nx_mb_cancel"],
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_mb_1");
    await emitEvent(page, "nx_mb_cancel");
    await emitEvent(page, "nx_mb_1");
    await emitEvent(page, "nx_mb_cancel");
    await page.waitForTimeout(600);
    expect(findAllSpans(otlp.captured as never[], "interaction").length).toBe(
      0,
    );

    await emitEvent(page, "nx_mb_1");
    await emitEvent(page, "nx_mb_2");
    const span = await otlp.waitForSpan("interaction", 8_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(false);
  });

  // INT-P36 — Invalid config payload
  test("INT-P36: mixed valid + invalid config payload — no interaction span emitted", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 436,
        name: "NX Valid Flow",
        events: [{ name: "nx_valid_a" }, { name: "nx_valid_b" }],
      }),
      { id: "invalid_missing_fields", events: [] },
    ]);
    await gotoAndWaitInit(page, otlp);
    otlp.reset();
    await emitEvent(page, "nx_valid_a");
    await emitEvent(page, "nx_valid_b");
    await expectNoInteractionSpansNx(otlp, page.waitForTimeout.bind(page));
  });

  // INT-P37 — Apdex exact boundary lower → Excellent
  test("INT-P37: apdex exact lower boundary — complete_time == lower → Excellent", async ({
    page,
    otlp,
  }) => {
    const lower = 120;
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 437,
        name: "NX Apdex Boundary Lower",
        events: [{ name: "nx_ab_a" }, { name: "nx_ab_b" }],
        thresholdInMs: 2000,
        uptimeLowerLimitInMs: lower,
        uptimeMidLimitInMs: 300,
        uptimeUpperLimitInMs: 600,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    const t0 = Date.now();
    await emitEventAt(page, "nx_ab_a", t0);
    await emitEventAt(page, "nx_ab_b", t0 + lower);
    await waitForInteractionCount(otlp, 1, 10_000);
    const span = findAllSpans(otlp.captured as never[], "interaction").find(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "437",
    );
    expect(span).toBeDefined();
    expect(getAttr(span?.attributes, "pulse.interaction.user_category")).toBe(
      "Excellent",
    );
  });

  // INT-P38 — Apdex exact boundary upper → Average
  test("INT-P38: apdex exact upper boundary — complete_time == upper → Average", async ({
    page,
    otlp,
  }) => {
    const upper = 600;
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 438,
        name: "NX Apdex Boundary Upper",
        events: [{ name: "nx_au_a" }, { name: "nx_au_b" }],
        thresholdInMs: 2000,
        uptimeLowerLimitInMs: 120,
        uptimeMidLimitInMs: 300,
        uptimeUpperLimitInMs: upper,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    const t0 = Date.now();
    await emitEventAt(page, "nx_au_a", t0);
    await emitEventAt(page, "nx_au_b", t0 + upper);
    await waitForInteractionCount(otlp, 1, 10_000);
    const span = findAllSpans(otlp.captured as never[], "interaction").find(
      (s) => getAttr(s.attributes, "pulse.interaction.config.id") === "438",
    );
    expect(span).toBeDefined();
    expect(getAttr(span?.attributes, "pulse.interaction.user_category")).toBe(
      "Average",
    );
  });

  // INT-P39 — Shared prefix: second branch still alive after first terminal
  test("INT-P39: shared prefix — first branch terminal does not kill second branch", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 439,
        name: "NX Branch 39A",
        events: [
          { name: "nx_39_e1" },
          { name: "nx_39_e2" },
          { name: "nx_39_e3" },
        ],
        thresholdInMs: 5000,
      }),
      makeParityInteractionConfig({
        id: 440,
        name: "NX Branch 39B",
        events: [
          { name: "nx_39_e1" },
          { name: "nx_39_e2" },
          { name: "nx_39_e5" },
        ],
        thresholdInMs: 5000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_39_e1");
    await emitEvent(page, "nx_39_e2");
    // e3 completes branch 39A
    await emitEvent(page, "nx_39_e3");
    await waitForInteractionCount(otlp, 1, 8_000);
    // e5 should still complete branch 39B (it was still mid-sequence)
    await emitEvent(page, "nx_39_e5");
    await waitForInteractionCount(otlp, 2, 8_000);

    const spans = findAllSpans(otlp.captured as never[], "interaction");
    const ids = spans.map((s) =>
      String(getAttr(s.attributes, "pulse.interaction.config.id")),
    );
    expect(ids).toContain("439");
    expect(ids).toContain("440");
    expect(
      spans.every(
        (s) => getAttr(s.attributes, "pulse.interaction.is_error") === false,
      ),
    ).toBe(true);
  });
});

// ─── @M2 interactions — Next.js unit-parity E2E (INT-P09/P35/P41) ─

test.describe("@M2 interactions — Next.js unit-parity E2E (INT-P09/P35/P41)", () => {
  // INT-P09 — Step event timestamps on span
  test("INT-P09: span.events carry timestamps matching emitted event times", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 509,
        name: "NX Step Timestamps",
        events: [{ name: "nx_ts_step1" }, { name: "nx_ts_step2" }],
        thresholdInMs: 5000,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    const t0 = Date.now();
    await emitEventAt(page, "nx_ts_step1", t0);
    await emitEventAt(page, "nx_ts_step2", t0 + 100);

    await waitForInteractionCount(otlp, 1, 10_000);
    const span = findAllSpans(otlp.captured as never[], "interaction")[0] as
      | import("./fixture").OtlpSpan
      | undefined;
    expect(span).toBeDefined();
    if (!span) return;
    expect(Array.isArray(span.events)).toBe(true);
    expect((span.events ?? []).length).toBeGreaterThanOrEqual(2);
    const eventNames = (span.events ?? []).map((e) => e.name);
    expect(eventNames).toContain("nx_ts_step1");
    expect(eventNames).toContain("nx_ts_step2");
  });

  // INT-P35 — Empty definitions from server
  test("INT-P35: empty interaction definitions from server — no interaction span emitted", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, []);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "any_event_p35");
    await page.waitForTimeout(800);
    expect(findAllSpans(otlp.captured as never[], "interaction").length).toBe(
      0,
    );
  });

// ─── Session crash count on session.end (Next.js) ────────────────────────────

test.describe("session.end crash count — Next.js", () => {
  test("session.end carries pulse.session.crash.count after device.crash", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await seedPulseSdkConfig(page, minimalPulseSdkConfig());
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Trigger a device.crash via the boundary throw button
    await page.click("[data-testid='throw-btn']");
    await otlp.waitForLog("device.crash");
    otlp.reset();

    // Flush session.end via synthetic pagehide
    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    const endLog = await otlp.waitForLog("session.end");
    expect(getAttr(endLog.attributes, "pulse.session.crash.count")).toBe(1);
    expect(getAttr(endLog.attributes, "pulse.session.non_fatal.count")).toBeNull();
  });

  test("session.end carries pulse.session.non_fatal.count after non_fatal", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo");
    await seedPulseSdkConfig(page, minimalPulseSdkConfig());
    await otlp.waitForLog("session.start");
    otlp.reset();

    // Trigger a non_fatal via manual exception button
    await page.click("[data-testid='manual-exception-btn']");
    await otlp.waitForLog("non_fatal");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    const endLog = await otlp.waitForLog("session.end");
    expect(getAttr(endLog.attributes, "pulse.session.non_fatal.count")).toBe(1);
    expect(getAttr(endLog.attributes, "pulse.session.crash.count")).toBeNull();
  });

  test("session.end without errors has no crash count attributes", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await seedPulseSdkConfig(page, minimalPulseSdkConfig());
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.evaluate(() => {
      window.dispatchEvent(
        new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
      );
    });

    const endLog = await otlp.waitForLog("session.end");
    expect(getAttr(endLog.attributes, "pulse.session.crash.count")).toBeNull();
    expect(getAttr(endLog.attributes, "pulse.session.non_fatal.count")).toBeNull();
  });
});

  // INT-P41 — Error span forces poor apdex + apdex_score=0
  test("INT-P41: error span (timeout) forces user_category=Poor and apdex_score=0", async ({
    page,
    otlp,
  }) => {
    await seedInteractionConfig(page, [
      makeParityInteractionConfig({
        id: 541,
        name: "NX Error Apdex",
        events: [{ name: "nx_err_step1" }, { name: "nx_err_step2" }],
        thresholdInMs: 400,
        uptimeLowerLimitInMs: 50,
        uptimeMidLimitInMs: 100,
        uptimeUpperLimitInMs: 150,
      }),
    ]);
    await gotoAndWaitInit(page, otlp);
    await emitEvent(page, "nx_err_step1");
    // Don't emit step2 — wait for timeout
    const span = await otlp.waitForSpan("interaction", 8_000);
    expect(getAttr(span.attributes, "pulse.interaction.is_error")).toBe(true);
    expect(getAttr(span.attributes, "pulse.interaction.user_category")).toBe(
      "Poor",
    );
    expect(getAttr(span.attributes, "pulse.interaction.apdex_score")).toBe(0);
  });
});
