import { AnalyticsParams } from "./analyticsConstants";

const mockReactGAEvent = jest.fn();
const mockForwardPulse = jest.fn();

jest.mock("react-ga4", () => ({
  __esModule: true,
  default: {
    initialize: jest.fn(),
    event: (...args: unknown[]) => mockReactGAEvent(...args),
    send: jest.fn(),
    gtag: jest.fn(),
  },
}));

jest.mock("../../pulse-web-rum/pulseRumBridge", () => ({
  forwardPulseEventFromLogEvent: (...args: unknown[]) =>
    mockForwardPulse(...args),
}));

const ORIGINAL_ENV = process.env;

describe("logEvent Pulse bridge", () => {
  beforeEach(() => {
    jest.resetModules();
    mockReactGAEvent.mockClear();
    mockForwardPulse.mockClear();
    process.env = { ...ORIGINAL_ENV };
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  async function loadLogEvent() {
    const mod = await import("./index");
    return mod.logEvent;
  }

  it("forwards to Pulse when GA is disabled", async () => {
    delete process.env.REACT_APP_GA_MEASUREMENT_ID;
    process.env.REACT_APP_PULSE_WEB_API_KEY = "test-key";
    const logEvent = await loadLogEvent();

    logEvent("User logged in", "login", "User");

    expect(mockReactGAEvent).not.toHaveBeenCalled();
    expect(mockForwardPulse).toHaveBeenCalledWith({
      action: "User logged in",
      label: "login",
      category: "User",
      value: undefined,
      additionalParams: undefined,
    });
  });

  it("fires GA and Pulse when both are enabled", async () => {
    process.env.REACT_APP_GA_MEASUREMENT_ID = "G-TEST";
    const logEvent = await loadLogEvent();

    logEvent("User logged in", "login", "User", 1, { extra: "x" });

    expect(mockReactGAEvent).toHaveBeenCalledWith({
      category: "User",
      action: "User logged in",
      label: "login",
      value: 1,
      extra: "x",
    });
    expect(mockForwardPulse).toHaveBeenCalled();
  });

  it("strips pulse_event from GA payload but still forwards full additionalParams", async () => {
    process.env.REACT_APP_GA_MEASUREMENT_ID = "G-TEST";
    const logEvent = await loadLogEvent();

    logEvent("click", "lbl", "User", undefined, {
      [AnalyticsParams.PULSE_EVENT]: "override_event",
      screen: "home",
    });

    expect(mockReactGAEvent).toHaveBeenCalledWith({
      category: "User",
      action: "click",
      label: "lbl",
      value: undefined,
      screen: "home",
    });
    expect(mockForwardPulse).toHaveBeenCalledWith(
      expect.objectContaining({
        additionalParams: {
          [AnalyticsParams.PULSE_EVENT]: "override_event",
          screen: "home",
        },
      }),
    );
  });

  it("still forwards unmapped actions to bridge (bridge no-ops internally)", async () => {
    delete process.env.REACT_APP_GA_MEASUREMENT_ID;
    const logEvent = await loadLogEvent();

    logEvent("unmapped_action");

    expect(mockForwardPulse).toHaveBeenCalledWith(
      expect.objectContaining({ action: "unmapped_action" }),
    );
  });
});
