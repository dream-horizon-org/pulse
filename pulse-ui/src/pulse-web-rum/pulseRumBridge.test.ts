import { Pulse } from "@dreamhorizonorg/pulse-web";
import {
  AnalyticsLabels,
  AnalyticsParams,
} from "../helpers/googleAnalytics/analyticsConstants";

const mockTrackEvent = jest.fn();

jest.mock("@dreamhorizonorg/pulse-web", () => ({
  Pulse: {
    isInitialized: jest.fn(),
    whenReady: jest.fn(),
    trackEvent: (...args: unknown[]) => mockTrackEvent(...args),
  },
}));

jest.mock("./pulseRumConfig", () => ({
  isPulseRumEnabled: jest.fn(() => true),
}));

jest.mock("./pulseEventContext", () => ({
  withPulseEventContext: jest.fn((attrs?: Record<string, unknown>) => ({
    tenant_id: "t1",
    ...attrs,
  })),
}));

const mockIsInitialized = Pulse.isInitialized as jest.Mock;
const mockWhenReady = Pulse.whenReady as jest.Mock;

describe("sanitizeAttributes", () => {
  beforeEach(() => {
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
    mockWhenReady.mockResolvedValue(undefined);
    mockTrackEvent.mockClear();
  });

  it("strips null and undefined, keeps falsy non-null values", async () => {
    const { sanitizeAttributes } = await import("./pulseRumBridge");
    expect(
      sanitizeAttributes({
        keep: "yes",
        dropNull: null,
        dropUndef: undefined,
        count: 0,
        flag: false,
      }),
    ).toEqual({ keep: "yes", count: 0, flag: false });
  });
});

describe("resolvePulseEventName", () => {
  beforeEach(() => jest.resetModules());

  it("returns pulse_event override from additionalParams", async () => {
    const { resolvePulseEventName } = await import("./pulseRumBridge");
    expect(
      resolvePulseEventName("anything", "label", {
        [AnalyticsParams.PULSE_EVENT]: "custom_event",
      }),
    ).toBe("custom_event");
  });

  it("returns map hit for User logged in", async () => {
    const { resolvePulseEventName } = await import("./pulseRumBridge");
    expect(resolvePulseEventName("User logged in")).toBe("user_logged_in");
  });

  it("returns map hit for logout composite key", async () => {
    const { resolvePulseEventName } = await import("./pulseRumBridge");
    expect(
      resolvePulseEventName("logout", AnalyticsLabels.USER_LOGGED_OUT),
    ).toBe("user_logged_out");
  });

  it("returns null for unmapped action", async () => {
    const { resolvePulseEventName } = await import("./pulseRumBridge");
    expect(resolvePulseEventName("unmapped_noise")).toBeNull();
  });
});

describe("forwardPulseCustomEvent", () => {
  beforeEach(() => {
    jest.resetModules();
    mockTrackEvent.mockClear();
    mockWhenReady.mockResolvedValue(undefined);
  });

  it("no-ops when RUM is disabled", async () => {
    const { isPulseRumEnabled } = await import("./pulseRumConfig");
    (isPulseRumEnabled as jest.Mock).mockReturnValue(false);
    const { forwardPulseCustomEvent } = await import("./pulseRumBridge");

    forwardPulseCustomEvent("skipped");

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });

  it("queues when Pulse is not initialized", async () => {
    mockIsInitialized.mockReturnValue(false);
    const { forwardPulseCustomEvent } = await import("./pulseRumBridge");

    forwardPulseCustomEvent("queued_event", { foo: "bar" });

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });

  it("calls Pulse.trackEvent with enriched attrs when ready", async () => {
    mockIsInitialized.mockReturnValue(true);
    const { forwardPulseCustomEvent } = await import("./pulseRumBridge");

    forwardPulseCustomEvent("ready_event", { source: "test" });

    expect(mockTrackEvent).toHaveBeenCalledWith("ready_event", {
      tenant_id: "t1",
      source: "test",
    });
  });
});

describe("flushPendingPulseEvents", () => {
  beforeEach(() => {
    jest.resetModules();
    mockTrackEvent.mockClear();
    mockWhenReady.mockResolvedValue(undefined);
  });

  it("drains queue after whenReady", async () => {
    mockIsInitialized.mockReturnValue(false);
    const bridge = await import("./pulseRumBridge");

    bridge.forwardPulseCustomEvent("drained", { x: 1 });
    expect(mockTrackEvent).not.toHaveBeenCalled();

    mockIsInitialized.mockReturnValue(true);
    await bridge.flushPendingPulseEvents();

    expect(mockWhenReady).toHaveBeenCalled();
    expect(mockTrackEvent).toHaveBeenCalledWith("drained", {
      tenant_id: "t1",
      x: 1,
    });
  });
});

describe("forwardPulseEventFromLogEvent", () => {
  beforeEach(() => {
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
    mockWhenReady.mockResolvedValue(undefined);
    mockTrackEvent.mockClear();
  });

  it("forwards mapped login action to user_logged_in", async () => {
    const { forwardPulseEventFromLogEvent } = await import("./pulseRumBridge");

    forwardPulseEventFromLogEvent({
      action: "User logged in",
      label: "login",
      category: "User",
    });

    expect(mockTrackEvent).toHaveBeenCalledWith(
      "user_logged_in",
      expect.objectContaining({
        label: "login",
        category: "User",
      }),
    );
  });

  it("no-ops for unmapped GA action", async () => {
    const { forwardPulseEventFromLogEvent } = await import("./pulseRumBridge");

    forwardPulseEventFromLogEvent({ action: "random_ga_noise" });

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });
});
