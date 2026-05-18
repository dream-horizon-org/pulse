/**
 * IndexedDB / offline persistence gap-close (PER-03/04/05).
 * Run via `yarn e2e:persistence` — not in default web-sdk-gates (heavier).
 */
import { test, expect, findAllLogsByBody } from "./fixture";

const E2E_OTLP_CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "Content-Type, Content-Encoding, X-API-KEY, X-Pulse-Metering-Session-ID",
} as const;

async function idbSignalCount(
  page: import("@playwright/test").Page,
): Promise<number> {
  return page.evaluate(async () => {
    return new Promise<number>((resolve, reject) => {
      const req = indexedDB.open("pulse_signal_buffer", 2);
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains("signals")) {
          resolve(0);
          return;
        }
        const tx = db.transaction("signals", "readonly");
        const countReq = tx.objectStore("signals").count();
        countReq.onsuccess = () => resolve(countReq.result);
        countReq.onerror = () => reject(countReq.error);
      };
    });
  });
}

test.describe("@Persistence PER-03 offline buffer", () => {
  test("PER-03: offline trackEvent buffers IndexedDB then drains online", async ({
    page,
    otlp,
  }) => {
    test.setTimeout(90_000);
    let logPosts = 0;
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      logPosts += 1;
      if (logPosts === 1) {
        await route.fulfill({
          status: 503,
          headers: E2E_OTLP_CORS,
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
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.context().setOffline(true);
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("per03_offline_probe");
    });
    await page.waitForTimeout(1500);
    const buffered = await idbSignalCount(page);
    expect(buffered).toBeGreaterThan(0);

    await page.context().setOffline(false);
    await page.waitForTimeout(4000);
    const logs = findAllLogsByBody(otlp.captured, "per03_offline_probe");
    expect(logs.length).toBeGreaterThanOrEqual(1);
    await expect.poll(() => idbSignalCount(page), { timeout: 20_000 }).toBe(0);
  });
});

test.describe("@Persistence PER-04 reload replay", () => {
  test("PER-04: failed export survives reload and replays to OTLP", async ({
    page,
    otlp,
  }) => {
    test.setTimeout(90_000);
    let logPosts = 0;
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      logPosts += 1;
      if (logPosts === 1) {
        await route.fulfill({
          status: 400,
          headers: E2E_OTLP_CORS,
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
        headers: E2E_OTLP_CORS,
        body: '{"partialSuccess":{}}',
      });
    });

    await page.goto("/");
    await page.waitForTimeout(3500);
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("per04_reload_probe");
    });
    await page.waitForTimeout(2000);

    otlp.reset();
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect
      .poll(
        () => findAllLogsByBody(otlp.captured, "per04_reload_probe").length,
        { timeout: 25_000 },
      )
      .toBeGreaterThanOrEqual(1);
  });
});

test.describe("@Persistence PER-05 logs retry", () => {
  test("PER-05: 503 then 200 exports exactly one matching log", async ({
    page,
    otlp,
  }) => {
    test.setTimeout(60_000);
    let posts = 0;
    await page.route("**/v1/logs", async (route) => {
      if (route.request().method() === "OPTIONS") {
        await route.fulfill({ status: 204, headers: E2E_OTLP_CORS });
        return;
      }
      posts += 1;
      const status = posts === 1 ? 503 : 200;
      if (status === 200) {
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
      }
      await route.fulfill({
        status,
        headers: E2E_OTLP_CORS,
        body: status === 200 ? '{"partialSuccess":{}}' : "{}",
      });
    });

    await page.goto("/");
    await otlp.waitForLog("session.start");
    otlp.reset();
    await page.evaluate(() => {
      (
        window as unknown as { Pulse: { trackEvent: (n: string) => void } }
      ).Pulse.trackEvent("per05_retry_probe");
    });
    await expect
      .poll(
        () => findAllLogsByBody(otlp.captured, "per05_retry_probe").length,
        { timeout: 20_000 },
      )
      .toBe(1);
    expect(posts).toBeGreaterThanOrEqual(2);
  });
});
