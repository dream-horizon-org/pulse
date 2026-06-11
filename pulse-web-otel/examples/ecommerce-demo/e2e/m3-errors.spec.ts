import type { Page } from "@playwright/test";
import { test, expect, getAttr, findAllLogs } from "./fixture";
import type { OtlpFixture } from "./fixture";
import {
  blockActiveConfigFetch,
  minimalPulseSdkConfig,
  seedPulseSdkConfig,
  waitPastSeededSignalsBatchWindow,
} from "./test-sdk-config";
import {
  assertCommonLogContract,
  assertDeviceCrashSeverity,
  assertErrorLocationAttrs,
  assertMultilineExceptionStack,
  assertNonFatalSeverity,
  assertStackLineCount,
} from "./error-contract-helpers";

async function gotoErrorDemo(page: Page, otlp: OtlpFixture): Promise<void> {
  test.setTimeout(60_000);
  await page.goto("/error-demo");
  await page.waitForSelector('[data-testid="throw-uncaught"]', {
    timeout: 50_000,
  });
  await otlp.waitForLog("session.start");
  otlp.reset();
}

test.describe("@M3-errors contract floor", () => {
  test("uncaught JS error emits device.crash with finite numeric attrs", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();

    const log = await otlp.waitForLog("device.crash");
    assertCommonLogContract(log, "device.crash");
    assertDeviceCrashSeverity(log);
    assertStackLineCount(log, 1);
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(getAttr(log.attributes, "non_fatal.is_manual")).toBeUndefined();

    const line = Number(getAttr(log.attributes, "error.lineno"));
    const col = Number(getAttr(log.attributes, "error.colno"));
    expect(Number.isFinite(line)).toBe(true);
    expect(Number.isFinite(col)).toBe(true);
    expect(line).toBeGreaterThan(0);
  });

  test("unhandled rejection emits non_fatal with manual=false", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-promise").click();

    const log = await otlp.waitForLog("non_fatal");
    assertCommonLogContract(log, "non_fatal");
    assertNonFatalSeverity(log);
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(getAttr(log.attributes, "non_fatal.is_manual")).toBe(false);
    expect(getAttr(log.attributes, "exception.type")).toBe("TypeError");
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Demo TypeError rejection from ErrorDemo",
    );
    const stack = getAttr(log.attributes, "exception.stacktrace");
    expect(stack).toBeDefined();
    expect(typeof stack).toBe("string");
  });

  test("manual reportException emits non_fatal with manual=true", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("report-exception").click();

    const log = await otlp.waitForLog("non_fatal");
    assertCommonLogContract(log, "non_fatal");
    expect(getAttr(log.attributes, "non_fatal.is_manual")).toBe(true);
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Manually reported error",
    );
  });

  test("render error boundary emits device.crash with component stack", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-render-error").click();

    const log = await otlp.waitForLog("device.crash");
    assertCommonLogContract(log, "device.crash");
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Intentional render error from ErrorDemo",
    );
    expect(getAttr(log.attributes, "react.component_stack")).toBeTruthy();
  });
});

test.describe("@M3-errors lifecycle and edge behavior", () => {
  test("same error burst within dedupe window exports once", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught-burst").click();
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter(
      (log) =>
        getAttr(log.attributes, "exception.message") ===
        "Demo dedupe burst error",
    );
    expect(crashes).toHaveLength(1);
    assertCommonLogContract(crashes[0]!, "device.crash");
  });

  test("same fingerprint emits again after dedupe window reset", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught-burst").click();
    await page.waitForTimeout(5_300);
    await page.getByTestId("throw-uncaught-burst").click();
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter(
      (log) =>
        getAttr(log.attributes, "exception.message") ===
        "Demo dedupe burst error",
    );
    expect(crashes).toHaveLength(2);
  });

  test("different fingerprints are not deduplicated", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "Distinct error A",
          filename: "error-demo.tsx",
          lineno: 301,
          colno: 7,
          error: new Error("Distinct error A"),
        }),
      );
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "Distinct error B",
          filename: "error-demo.tsx",
          lineno: 302,
          colno: 8,
          error: new Error("Distinct error B"),
        }),
      );
    });
    await page.waitForTimeout(700);

    const crashes = findAllLogs(otlp.captured, "device.crash").filter((log) => {
      const message = String(getAttr(log.attributes, "exception.message"));
      return message === "Distinct error A" || message === "Distinct error B";
    });
    expect(crashes).toHaveLength(2);
  });

  test("string and undefined rejection reasons are normalized", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);

    await page.getByTestId("throw-promise-string").click();
    const stringLog = await otlp.waitForLog("non_fatal");
    assertCommonLogContract(stringLog, "non_fatal");
    expect(getAttr(stringLog.attributes, "exception.type")).toBe("Error");
    expect(getAttr(stringLog.attributes, "exception.message")).toBe("string");

    otlp.reset();
    await page.getByTestId("throw-promise-undefined").click();
    const undefinedLog = await otlp.waitForLog("non_fatal");
    assertCommonLogContract(undefinedLog, "non_fatal");
    expect(getAttr(undefinedLog.attributes, "exception.message")).toBe(
      "Unknown rejection",
    );
  });

  test("cross-origin script error emits stub device.crash (ISS-010)", async ({
    page,
    otlp,
  }) => {
    // ISS-010: cross-origin "Script error." with no filename now emits a stub device.crash
    // (empty stack/filename) instead of being silently dropped — matches Android behaviour.
    await gotoErrorDemo(page, otlp);
    await page.evaluate(() => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "Script error.",
          error: null,
        }),
      );
    });
    await page.waitForTimeout(600);

    const crashes = findAllLogs(otlp.captured, "device.crash");
    expect(crashes).toHaveLength(1);
    const stub = crashes[0]!;
    expect(getAttr(stub.attributes, "exception.message")).toBe("Script error.");
    expect(getAttr(stub.attributes, "exception.stacktrace")).toBe("");
    expect(getAttr(stub.attributes, "error.filename")).toBe("");
    expect(getAttr(stub.attributes, "url.path")).toBeTruthy();
    // No non_fatal should have been emitted
    expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  });

  test("error log timestamp is emitted near trigger time", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    const beforeMs = Date.now();
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    const afterMs = Date.now();

    expect(log.timeUnixNano).toBeTruthy();
    const tsMs = Number(BigInt(log.timeUnixNano as string) / 1_000_000n);
    expect(tsMs).toBeGreaterThanOrEqual(beforeMs - 2_000);
    expect(tsMs).toBeLessThanOrEqual(afterMs + 2_000);
  });

  test("existing window error listener still receives events", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      const fired: string[] = [];
      (window as unknown as Record<string, unknown>)[
        "__existingErrorHandlerFired"
      ] = fired;
      window.addEventListener("error", (event) => {
        fired.push(event.message);
      });
    });

    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const crash = await otlp.waitForLog("device.crash");
    expect(getAttr(crash.attributes, "exception.message")).toBe(
      "Demo uncaught error from ErrorDemo",
    );

    const existingFired = await page.evaluate(
      () =>
        (window as unknown as Record<string, string[]>)[
          "__existingErrorHandlerFired"
        ] ?? [],
    );
    expect(
      existingFired.some((message) =>
        message.includes("Demo uncaught error from ErrorDemo"),
      ),
    ).toBe(true);
  });
});

test.describe("@M3-errors gate and consent", () => {
  test("js_crash gate off exports zero error logs after reset", async ({
    page,
    otlp,
  }) => {
    const cfg = minimalPulseSdkConfig({
      version: 920,
      features: [
        {
          featureName: "js_crash",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
      ],
    });
    await seedPulseSdkConfig(page, cfg);
    await blockActiveConfigFetch(page);
    await page.goto("/error-demo");

    await otlp.waitForLog("session.start");
    otlp.reset();

    await page.getByTestId("throw-uncaught").click();
    await page.getByTestId("throw-promise").click();
    await waitPastSeededSignalsBatchWindow(page);

    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
    expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  });

  test("DENIED consent keeps SDK inactive and exports zero errors", async ({
    page,
    otlp,
  }) => {
    await page.goto("/error-demo?pulse_consent=denied");
    await page.waitForSelector('[data-testid="throw-uncaught"]');
    await page.getByTestId("throw-uncaught").click();
    await page.getByTestId("throw-promise").click();
    await page.waitForTimeout(700);

    expect(findAllLogs(otlp.captured, "session.start")).toHaveLength(0);
    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
    expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  });
});

test.describe("@M3-errors strict manual parity", () => {
  test("ERR-05: handled try/catch exports zero device.crash", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-handled-catch").click();
    await waitPastSeededSignalsBatchWindow(page);
    expect(findAllLogs(otlp.captured, "device.crash")).toHaveLength(0);
    expect(findAllLogs(otlp.captured, "non_fatal")).toHaveLength(0);
  });

  test("ERR-07: device.crash has error.filename, error.lineno, error.colno", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    assertErrorLocationAttrs(log);
  });

  test("ERR-09: exception.stacktrace is multi-line with frames", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    assertMultilineExceptionStack(log, 2);
  });

  test("ERR-12/ERR-13: device.crash SeverityText FATAL and SeverityNumber 21", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    assertDeviceCrashSeverity(log);
  });

  test("ERR-15: exception.message matches thrown error", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    expect(getAttr(log.attributes, "exception.message")).toBe(
      "Demo uncaught error from ErrorDemo",
    );
  });

  test("ERR-16: exception.stacktrace multi-line on same-origin crash", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    assertMultilineExceptionStack(log, 2);
  });

  test("ERR-25/ERR-26: non_fatal SeverityText WARN and SeverityNumber 13", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-promise").click();
    const log = await otlp.waitForLog("non_fatal");
    assertNonFatalSeverity(log);
  });

  test("ERR-27: rejection preserves TypeError exception.type", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-promise").click();
    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "exception.type")).toBe("TypeError");
  });

  test("ERR-28: Promise.reject(string) unwraps exception.message", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-promise-string").click();
    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "exception.message")).toBe("string");
  });

  test("ERR-29: exception.stacktrace is always a string (may be empty)", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    const stack = getAttr(log.attributes, "exception.stacktrace");
    expect(stack).toBeDefined();
    expect(typeof stack).toBe("string");
  });

  test("ERR-30: non_fatal url.path is pathname only", async ({
    page,
    otlp,
  }) => {
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-promise").click();
    const log = await otlp.waitForLog("non_fatal");
    expect(getAttr(log.attributes, "url.path")).toBe("/error-demo");
    expect(String(getAttr(log.attributes, "url.path")).startsWith("http")).toBe(
      false,
    );
  });
});

test.describe("@M3-errors device state attrs", () => {
  test("ERR-06: device.crash includes battery.percent", async ({
    page,
    otlp,
  }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, "getBattery", {
        configurable: true,
        value: async () => ({
          level: 0.42,
          charging: false,
          chargingTime: 0,
          dischargingTime: Infinity,
          addEventListener: () => {},
          removeEventListener: () => {},
        }),
      });
    });
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    const pct = getAttr(log.attributes, "battery.percent");
    expect(typeof pct).toBe("number");
    expect(pct).toBeCloseTo(42, 0);
  });

  test("ERR-22: device.crash includes storage.free", async ({ page, otlp }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, "storage", {
        configurable: true,
        value: {
          estimate: async () => ({ usage: 1_000, quota: 5_000_000_000 }),
        },
      });
    });
    await gotoErrorDemo(page, otlp);
    await page.getByTestId("throw-uncaught").click();
    const log = await otlp.waitForLog("device.crash");
    const free = getAttr(log.attributes, "storage.free");
    expect(typeof free).toBe("number");
    expect(Number(free)).toBeGreaterThan(0);
  });
});
