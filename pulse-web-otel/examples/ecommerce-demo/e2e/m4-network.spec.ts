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
  findAllNetworkSpans,
  type OtlpSpan,
} from "./fixture";
import {
  seedPulseSdkConfig,
  minimalPulseSdkConfig,
  blockActiveConfigFetch,
} from "./test-sdk-config";

async function waitForPulseWebInitialized(page: Page): Promise<void> {
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

test.describe("@M4 network e2e", () => {
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

    await page.evaluate(() => {
      void fetch("/pulse-e2e-network/data?token=secret", { method: "GET" });
    });

    await page.waitForTimeout(600);

    const span = await pollProbeHttpSpan(otlp, "pulse-e2e-network");

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

    await page.waitForTimeout(600);

    const span = await pollProbeHttpSpan(otlp, "xhr-probe");

    expect(getAttr(span.attributes, "pulse.type")).toBe("network.200");
    expect(getAttr(span.attributes, "http.request.method")).toBe("GET");
    expectFiniteNumberAttr(span.attributes, "http.response.status_code");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(200);

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
    await page.waitForTimeout(1200);

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

    await page.evaluate(() => {
      void fetch("/pulse-e2e-network/gate-off-probe");
    });
    await page.waitForTimeout(600);

    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  test("E1: 404 fetch sets error.type 4xx", async ({ page, otlp }) => {
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

    await page.evaluate(() => {
      void fetch("/pulse-e2e-network/err-404");
    });
    await page.waitForTimeout(600);

    const span = await pollProbeHttpSpan(otlp, "err-404");

    expect(getAttr(span.attributes, "pulse.type")).toBe("network.404");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(404);
    expect(getAttr(span.attributes, "error.type")).toBe("4xx");
  });

  test("E1: 500 fetch sets error.type 5xx", async ({ page, otlp }) => {
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

    await page.evaluate(() => {
      void fetch("/pulse-e2e-network/err-500");
    });
    await page.waitForTimeout(600);

    const span = await pollProbeHttpSpan(otlp, "err-500");

    expect(getAttr(span.attributes, "pulse.type")).toBe("network.500");
    expect(getAttr(span.attributes, "http.response.status_code")).toBe(500);
    expect(getAttr(span.attributes, "error.type")).toBe("5xx");
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

    await page.evaluate(() => {
      void fetch("/pulse-e2e-network/local-network-off");
    });
    await page.waitForTimeout(600);

    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });

  test("C1: DENIED consent — SDK does not start, no network client spans", async ({
    page,
    otlp,
  }) => {
    await page.goto("/?pulse_consent=denied");
    await page.waitForTimeout(1500);
    expect(findAllNetworkSpans(otlp.captured)).toHaveLength(0);
  });
});
