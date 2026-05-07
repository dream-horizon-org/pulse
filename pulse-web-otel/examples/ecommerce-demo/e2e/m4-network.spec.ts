/**
 * M4 — Network instrumentation (`pulse.type` `network.<code>` on OTLP trace spans).
 *
 * Checklist: `web-sdk-plan/v3-network/PLAN-B-network-http-spans.md`
 */
import type { Page } from "@playwright/test";
import {
  test,
  expect,
  getAttr,
  findAllLogs,
  findAllNetworkSpans,
  getOtlpSpanStatusCode,
  type OtlpSpan,
} from "./fixture";
import {
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  blockActiveConfigFetch,
} from "./test-sdk-config";

/** OTLP JSON span.status.code — matches {@link SpanStatusCode} from `@opentelemetry/api`. */
const OTLP_SPAN_STATUS_OK = 1;
const OTLP_SPAN_STATUS_ERROR = 2;

async function waitForPulseWebInitialized(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const w = window as unknown as {
            PulseWeb?: { isInitialized: () => boolean };
          };
          return w.PulseWeb?.isInitialized?.() ?? false;
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

function expectFiniteNumberAttr(
  attrs: Parameters<typeof getAttr>[0],
  key: string,
): void {
  const v = getAttr(attrs, key);
  expect(typeof v).toBe("number");
  expect(Number.isFinite(v)).toBe(true);
}

/** Spans whose `url.full` matches OTLP export paths (must not be traced as client HTTP). */
function httpSpansTargetingOtlpExport(captured: unknown[]): OtlpSpan[] {
  return findAllNetworkSpans(captured as never[]).filter((s) => {
    const full = String(getAttr(s.attributes, "url.full") ?? "");
    return /\/v1\/(traces|logs|metrics)(?:\?|$)/.test(full);
  });
}

/**
 * PLAN-B flush path: SDK listens for `pagehide` and `forceFlush()` trace batch processor.
 * Aligns E2E with lifecycle doc instead of relying only on {@code VITE_PULSE_BATCH_DELAY_MS}.
 */
async function flushTraceExport(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );
  });
  await page.waitForTimeout(400);
}

async function pollProbeHttpSpan(
  otlp: { captured: unknown[] },
  probeSubstring: string,
): Promise<OtlpSpan> {
  let found: OtlpSpan | undefined;
  await expect
    .poll(
      () => {
        found = findAllNetworkSpans(otlp.captured as never[]).find((s) =>
          String(getAttr(s.attributes, "url.full") ?? "").includes(
            probeSubstring,
          ),
        );
        return found;
      },
      { timeout: 15_000 },
    )
    .toBeDefined();
  return found!;
}

/** Last matching span — `url.full` may be absent on XHR timeout/abort when `responseURL` is empty. */
async function pollLastNetworkZeroTransportErrorSpan(
  otlp: { captured: unknown[] },
): Promise<OtlpSpan> {
  let found: OtlpSpan | undefined;
  await expect
    .poll(
      () => {
        const spans = findAllNetworkSpans(otlp.captured as never[]);
        found = [...spans].reverse().find((s) => {
          return (
            getAttr(s.attributes, "pulse.type") === "network.0" &&
            getAttr(s.attributes, "error.type") === "network_error"
          );
        });
        return found;
      },
      { timeout: 15_000 },
    )
    .toBeDefined();
  return found!;
}

test.describe("@M4 network e2e", () => {
  test("Network Lab: fetch GET button emits network.200 span", async ({
    page,
    otlp,
  }) => {
    await page.goto("/network-lab");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByTestId("network-lab-fetch-get-local").click();
    await expect(page.getByText("ok — status=200")).toBeVisible();

    await flushTraceExport(page);
    const span = await pollProbeHttpSpan(otlp, "/api/products.json");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  // OTel XMLHttpRequestInstrumentation ends the span (and runs applyCustomAttributesOnSpan)
  // on completion including timeout and abort — otherwise these assertions would never see a span.
  test("Network Lab: XHR timeout emits network.0, network_error, OTLP ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route("https://httpstat.us/**", async (route) => {
      await new Promise((r) => setTimeout(r, 10_000));
      await route.fulfill({ status: 200, body: "{}" });
    });

    await page.goto("/network-lab");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByTestId("network-lab-xhr-timeout").click();
    await expect(page.getByText(/xhr\.timeout/)).toBeVisible({
      timeout: 25_000,
    });

    await flushTraceExport(page);
    const span = await pollLastNetworkZeroTransportErrorSpan(otlp);

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("network_error");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("Network Lab: XHR abort emits network.0, network_error, OTLP ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route("https://httpstat.us/**", async (route) => {
      await new Promise((r) => setTimeout(r, 10_000));
      await route.fulfill({ status: 200, body: "{}" });
    });

    await page.goto("/network-lab");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByTestId("network-lab-xhr-abort").click();
    await expect(page.getByText(/xhr\.abort/)).toBeVisible({
      timeout: 25_000,
    });

    await flushTraceExport(page);
    const span = await pollLastNetworkZeroTransportErrorSpan(otlp);

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("network_error");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("Network Lab: fetch 404 button emits network.404 span", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/api/does-not-exist.json"),
      async (route) => {
        await route.fulfill({
          status: 404,
          headers: { "Content-Type": "application/json" },
          body: '{"error":"not_found"}',
        });
      },
    );

    await page.goto("/network-lab");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.getByTestId("network-lab-fetch-404").click();

    await flushTraceExport(page);
    const span = await pollProbeHttpSpan(otlp, "/api/does-not-exist.json");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.404");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(404);
    expect(getAttr(span.attributes, "error.type")).toBe("4xx");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("P1/P2/P4: fetch emits network.* span, strips query, optional http.duration numeric", async ({
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
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/data?token=secret", { method: "GET" });
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "pulse-e2e-network");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expectFiniteNumberAttr(span.attributes, "http.response.status_code");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);

    const full = String(getAttr(span.attributes, "url.full") ?? "");
    expect(full).not.toContain("token");
    expect(full).not.toContain("?");

    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expect(getAttr(span.attributes, "server.address")).toBeTruthy();
    expectFiniteNumberAttr(span.attributes, "server.port");

    const dur = getAttr(span.attributes, "http.duration");
    if (dur !== undefined) {
      expectFiniteNumberAttr(span.attributes, "http.duration");
    }
  });

  test("P3: XMLHttpRequest emits network.* span with contract attrs", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: '{"ok":true}',
        });
      },
    );

    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(() => {
      return new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("GET", "/pulse-e2e-network/xhr-probe?z=1");
        xhr.onload = () => resolve();
        xhr.onerror = () => reject(new Error("xhr failed"));
        xhr.send();
      });
    });

    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "xhr-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_OK);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expectFiniteNumberAttr(span.attributes, "http.response.status_code");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);
    expectFiniteNumberAttr(span.attributes, "server.port");

    const full = String(getAttr(span.attributes, "url.full") ?? "");
    expect(full).not.toContain("?");
    expect(full).toContain("xhr-probe");

    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("P5: OTLP export URLs are not traced as network client spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    await flushTraceExport(page);

    expect(httpSpansTargetingOtlpExport(otlp.captured)).toHaveLength(0);
  });

  test("G1: no network.* client spans when network_instrumentation gate is off", async ({
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
    await blockActiveConfigFetch(page);

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

  test("E1: 404 fetch sets error.type 4xx and OTLP span ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/err-404"),
      async (route) => {
        await route.fulfill({
          status: 404,
          headers: { "Content-Type": "application/json" },
          body: "{}",
        });
      },
    );

    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/err-404");
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "err-404");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.404");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(404);
    expect(getAttr(span.attributes, "error.type")).toBe("4xx");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("E1: 500 fetch sets error.type 5xx and OTLP span ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/err-500"),
      async (route) => {
        await route.fulfill({
          status: 500,
          body: "error",
        });
      },
    );

    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/err-500");
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "err-500");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.500");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(500);
    expect(getAttr(span.attributes, "error.type")).toBe("5xx");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("E3: cross-origin no-cors opaque response → cors_error, network.0, OTLP ERROR", async ({
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
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      /* Dev server is :3099; `localhost` vs `127.0.0.1` is cross-origin. `page.route` still
       * intercepts `/pulse-e2e-network/*` on 127.0.0.1. no-cors → opaque response, status 0 in Chromium. */
      await fetch("http://127.0.0.1:3099/pulse-e2e-network/cors-opaque/x", {
        mode: "no-cors",
        credentials: "omit",
      });
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "cors-opaque");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("cors_error");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("E4: route.abort failed fetch → network_error, network.0, OTLP ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/net-fail"),
      (route) => route.abort("failed"),
    );

    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      try {
        await fetch("/pulse-e2e-network/net-fail/x");
      } catch {
        /* expected — Playwright abort */
      }
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "net-fail");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("network_error");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("E5: aborted fetch → network_error, network.0, OTLP ERROR", async ({
    page,
    otlp,
  }) => {
    await page.route(
      (url) => url.pathname.includes("/pulse-e2e-network/abort-probe"),
      async (route) => {
        await route.fulfill({
          status: 200,
          headers: { "Content-Type": "application/json" },
          body: "{}",
        });
      },
    );

    await page.goto("/");
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      const ac = new AbortController();
      const p = fetch("/pulse-e2e-network/abort-probe/x", {
        signal: ac.signal,
      }).catch(() => undefined);
      ac.abort();
      await p;
    });
    await flushTraceExport(page);

    const span = await pollProbeHttpSpan(otlp, "abort-probe");

    expect(getOtlpSpanStatusCode(span)).toBe(OTLP_SPAN_STATUS_ERROR);
    expect(getAttr(span.attributes, "pulse.type")).toBe("network.0");
    expect(getAttr(span.attributes, "error.type")).toBe("network_error");
    expect(getAttr(span.attributes, "session.id")).toBeTruthy();
    expect(getAttr(span.attributes, "screen.name")).toBeTruthy();
  });

  test("E2: local instrumentations.network.enabled false yields no network client spans", async ({
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
    await waitForPulseWebInitialized(page);
    await otlp.waitForLog("session.start", 15_000);
    otlp.reset();

    await page.evaluate(async () => {
      await fetch("/pulse-e2e-network/local-network-off");
    });
    await flushTraceExport(page);

    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  test("C1: DENIED consent — no session.start, no network client spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(1500);
    expect(findAllLogs(otlp.captured, "session.start")).toHaveLength(0);
    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });
});
