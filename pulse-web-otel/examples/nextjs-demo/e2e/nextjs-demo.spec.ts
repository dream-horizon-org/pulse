/**
 * Next.js Demo — E2E Tests (mock OTLP, no ClickHouse required)
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
  getOtlpSpanStatusCode,
  getResourceAttr,
  type OtlpSpan,
} from "./fixture";
import {
  seedPulseSdkConfig,
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

  test("session ID is consistent across navigations", async ({ page, otlp }) => {
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
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      if (logs.some((lr) => getAttr(lr.attributes, "screen.name") === "/products")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected a log with screen.name = /products").toBe(true);
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
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      if (logs.some((lr) => getAttr(lr.attributes, "screen.name") === "/cart")) {
        found = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    expect(found, "Expected a log with screen.name = /cart").toBe(true);
  });

  test("screen.name updates on multi-hop navigation: / → /products → /cart", async ({
    page,
    otlp,
  }) => {
    const allScreenNames = (): string[] => {
      const logs = otlp.captured
        .filter((c) => c.type === "logs")
        .flatMap((c) =>
          c.type === "logs"
            ? c.body.resourceLogs.flatMap((rl) =>
                rl.scopeLogs.flatMap((sl) => sl.logRecords),
              )
            : [],
        );
      return logs
        .map((lr) => getAttr(lr.attributes, "screen.name") as string)
        .filter(Boolean);
    };

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

  test("reportException emits non_fatal with exception.type", async ({ page, otlp }) => {
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
    expect(String(getAttr(log.attributes, "url.path") ?? "").startsWith("http")).toBe(false);
  });

  test("reportDeviceCrash emits device.crash with exception.type", async ({ page, otlp }) => {
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
    expect(String(getAttr(log.attributes, "url.path") ?? "").startsWith("http")).toBe(false);
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

  test("non_fatal carries same session.id as session.start", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-exception-btn']");

    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });

  test("manual device.crash carries same session.id as session.start", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    const sessionStart = await otlp.waitForLog("session.start");
    const sessionId = getAttr(sessionStart.attributes, "session.id") as string;

    await page.click("[data-testid='manual-crash-btn']");

    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "session.id")).toBe(sessionId);
  });
});

test.describe("error signal contract", () => {
  test("ERR-02 / ERR-31 — unhandled rejection emits non_fatal WARN, is_manual=false (boolean)", async ({ page, otlp }) => {
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

  test("ERR-05 — handled try/catch does not emit device.crash", async ({ page, otlp }) => {
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

  test("ERR-17 — error.filename is defined (bundle URL or unknown, never absent)", async ({ page, otlp }) => {
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

  test("ERR-03 — same error burst within 5s emits only once (dedup)", async ({ page, otlp }) => {
    await page.goto("/error-demo");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.click("[data-testid='throw-burst']");
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter(
      (log) => getAttr(log.attributes, "exception.message") === "Burst dedup error",
    );
    expect(crashes).toHaveLength(1);
  });

  test("ERR-09 / ERR-14 / ERR-16 — TypeError: class name preserved + stacktrace multi-line", async ({ page, otlp }) => {
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
      (window as Window & { __TEST_PULSE_ERRORS_DISABLED?: boolean }).__TEST_PULSE_ERRORS_DISABLED =
        true;
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
          new PromiseRejectionEvent("unhandledrejection", { promise: p, reason: err }),
        );
      }
    });

    await page.waitForTimeout(700);
    const logs = findAllLogs(otlp.captured, "non_fatal").filter(
      (l) => getAttr(l.attributes, "exception.message") === "rejection-dedupe-burst",
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
        new PromiseRejectionEvent("unhandledrejection", { promise: p, reason: err }),
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
        new PromiseRejectionEvent("unhandledrejection", { promise: p, reason: err }),
      );
    });

    await page.waitForTimeout(700);
    const logs = findAllLogs(otlp.captured, "non_fatal").filter(
      (l) => getAttr(l.attributes, "exception.message") === "rejection-dedupe-window",
    );
    expect(logs).toHaveLength(2);
  });
});

// ─── Network instrumentation ──────────────────────────────────────────────────

const OTLP_SPAN_STATUS_OK = 1;

async function flushTraceExport(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));
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
          String(getAttr(s.attributes, "url.full") ?? "").includes(urlSubstring),
        );
        return found;
      },
      { timeout: 15_000 },
    )
    .toBeDefined();
  return found!;
}

test.describe("@M4 network — Next.js demo", () => {
  // ISS-N06: captureQueryParams:true keeps query params on url.full (sensitive ones redacted)
  test("ISS-N06: captureQueryParams:true keeps query params, redacts sensitive token", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({ status: 200, headers: { "Content-Type": "application/json" }, body: "{}" });
      },
    );

    await page.goto("/?pulse_capture_query=1");
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/query-probe?search=hello&token=supersecret");
    });
    await flushTraceExport(page);

    const span = await pollProbeNetworkSpan(otlp, "query-probe");
    const full = String(getAttr(span.attributes, "url.full") ?? "");

    expect(full).toContain("search=hello");
    expect(full).not.toContain("supersecret");
    expect(full).toContain("token=*");
  });

  // ISS-N07: blockedUrls prevents spans for matching URLs
  test("ISS-N07: blockedUrls config suppresses spans for matched URL", async ({
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
      String(getAttr(s.attributes, "url.full") ?? "").includes("blocked-endpoint"),
    );
    expect(blocked).toHaveLength(0);
  });

  // ISS-N08: peerServiceMap sets peer.service on matching spans
  test("ISS-N08: peerServiceMap sets peer.service attribute on spans", async ({
    page,
    otlp,
  }) => {
    const peerHost = "127.0.0.1:3003";

    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/peer-probe"),
      async (route) => {
        await route.fulfill({ status: 200, body: "{}" });
      },
    );

    await page.goto(
      `/?pulse_peer_host=${encodeURIComponent(peerHost)}&pulse_peer_service=catalogue-service`,
    );
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

  // ISS-N09: propagateTraceHeaderCorsUrls injects traceparent W3C header
  test("ISS-N09: propagateTraceHeaderCorsUrls injects W3C traceparent header on matching requests", async ({
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

    await page.goto(`/?pulse_propagate_cors=${encodeURIComponent("localhost:3003")}`);
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
    expect(String(getAttr(span.attributes, "url.full") ?? "")).not.toContain("?");
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

    await page.evaluate(() =>
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

  // NET-18: XHR capturedRequestHeaders — headers stored via WeakMap monkey-patch
  test("NET-18: XHR capturedRequestHeaders captures request headers on XHR spans", async ({
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
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(() =>
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
    expect(getAttr(span.attributes, "http.request.header.x-request-id")).toEqual(["test-req-abc"]);
    expect(getAttr(span.attributes, "http.request.header.x-custom-header")).toEqual(["captured-value"]);
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
