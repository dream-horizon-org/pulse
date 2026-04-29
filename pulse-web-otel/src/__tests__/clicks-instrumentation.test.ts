vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { logs } from "@opentelemetry/api-logs";

import { FeatureGate } from "../feature-gate";
import { InstrumentationRegistry } from "../instrumentation-registry";
import { ClicksInstrumentation } from "../instrumentations/clicks";
import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import type { SdkContext } from "../instrumentation-registry";
import type { PulseSdkConfig } from "../remote-config";
import { PulseWebSemconv } from "../semconv";

const keys = PulseWebSemconv.AttributeKey;
const pulseTypes = PulseWebSemconv.PulseType;
const logBodies = PulseWebSemconv.LogBody;
const clickKind = PulseWebSemconv.ClickTypeValue;

function makeSdk(overrides?: Partial<SdkContext["config"]>): SdkContext {
  return {
    endpointBaseUrl: "https://collector.example.com",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    sessionProvider: {
      onSessionChange: () => () => {},
      emitInitialSession: () => {},
    } as unknown as SdkContext["sessionProvider"],
    logger: {} as never,
    tracer: {} as never,
    config: {
      apiKey: "proj_abc_secret",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      ...overrides,
    },
    globalAttrsProcessor: {} as never,
  };
}

describe("ClicksInstrumentation", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  afterEach(() => {
    vi.mocked(logs.getLogger).mockClear();
  });

  it("emits app.widget.click log with good click on button", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({ emit } as never);

    document.body.innerHTML = '<button type="button">Go</button>';
    const btn = document.querySelector("button")!;

    const instr = new ClicksInstrumentation();
    instr.install(makeSdk());

    btn.dispatchEvent(
      new MouseEvent("click", {
        bubbles: true,
        cancelable: true,
        clientX: 10,
        clientY: 20,
      }),
    );

    expect(emit).toHaveBeenCalledTimes(1);
    const rec = emit.mock.calls[0]?.[0] as {
      body: string;
      attributes: Record<string, unknown>;
    };
    expect(rec.body).toBe(logBodies.APP_WIDGET_CLICK);
    expect(rec.attributes[keys.PULSE_TYPE]).toBe(pulseTypes.APP_CLICK);
    expect(rec.attributes[keys.CLICK_TYPE]).toBe(clickKind.GOOD);
    expect(rec.attributes[keys.APP_WIDGET_NAME]).toBe("BUTTON");
    expect(rec.attributes[keys.APP_SCREEN_COORDINATE_X]).toBe(10);
    expect(rec.attributes[keys.APP_SCREEN_COORDINATE_Y]).toBe(20);
  });

  it("emits dead click without widget attrs when only body in path", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({ emit } as never);

    const instr = new ClicksInstrumentation();
    instr.install(makeSdk());

    document.body.dispatchEvent(
      new MouseEvent("click", { bubbles: true, clientX: 1, clientY: 2 }),
    );

    expect(emit).toHaveBeenCalledTimes(1);
    const rec = emit.mock.calls[0]?.[0] as {
      attributes: Record<string, unknown>;
    };
    expect(rec.attributes[keys.CLICK_TYPE]).toBe(clickKind.DEAD);
    expect(rec.attributes[keys.APP_WIDGET_NAME]).toBeUndefined();
    expect(rec.attributes[keys.APP_WIDGET_ID]).toBeUndefined();
  });

  it("uninstall prevents further click logs", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({ emit } as never);

    document.body.innerHTML = "<button>x</button>";
    const btn = document.querySelector("button")!;

    const instr = new ClicksInstrumentation();
    instr.install(makeSdk());
    emit.mockClear();

    btn.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(emit).toHaveBeenCalledTimes(1);

    instr.uninstall();
    emit.mockClear();
    btn.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(emit).not.toHaveBeenCalled();
  });
});

describe("InstrumentationRegistry + clicks gate", () => {
  const disabledClickConfig: PulseSdkConfig = {
    ...DEFAULT_SDK_CONFIG,
    features: [
      {
        featureName: "click",
        sessionSampleRate: 0,
        sdks: ["pulse_web_js"],
      },
    ],
  };

  it("does not install ClicksInstrumentation when PulseFeature.CLICK is gated off", () => {
    const installSpy = vi.spyOn(ClicksInstrumentation.prototype, "install");

    const sdk = makeSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      new FeatureGate(disabledClickConfig),
      {},
    );

    registry.installAll();

    expect(installSpy).not.toHaveBeenCalled();
    installSpy.mockRestore();
  });

  it("installs ClicksInstrumentation when click feature is enabled", () => {
    const installSpy = vi.spyOn(ClicksInstrumentation.prototype, "install");

    const sdk = makeSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      new FeatureGate(DEFAULT_SDK_CONFIG),
      {},
    );

    registry.installAll();

    expect(installSpy).toHaveBeenCalledTimes(1);
    installSpy.mockRestore();
  });
});
