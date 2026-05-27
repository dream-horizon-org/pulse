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
    setUserId: jest.fn(),
    setUserProperties: jest.fn(),
    clearUserIdentity: jest.fn(),
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
const mockSetUserId = Pulse.setUserId as jest.Mock;

describe("sanitizeAttributes", () => {
  beforeEach(() => {
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
    mockWhenReady.mockResolvedValue(undefined);
    mockTrackEvent.mockClear();
  });

  it("strips null and undefined, keeps falsy non-null values", async () => {
    const { sanitizeAttributes } = await import("./pulseRum");
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

  it("returns undefined when all values are nullish", async () => {
    const { sanitizeAttributes } = await import("./pulseRum");
    expect(
      sanitizeAttributes({ onlyNull: null, onlyUndef: undefined }),
    ).toBeUndefined();
  });
});

describe("resolvePulseEventName", () => {
  beforeEach(() => jest.resetModules());

  it("returns pulse_event override from additionalParams", async () => {
    const { resolvePulseEventName } = await import("./pulseRum");
    expect(
      resolvePulseEventName("anything", "label", {
        [AnalyticsParams.PULSE_EVENT]: "custom_event",
      }),
    ).toBe("custom_event");
  });

  it("returns map hit for User logged in", async () => {
    const { resolvePulseEventName } = await import("./pulseRum");
    expect(resolvePulseEventName("User logged in")).toBe("user_logged_in");
  });

  it("returns map hit for logout composite key", async () => {
    const { resolvePulseEventName } = await import("./pulseRum");
    expect(
      resolvePulseEventName("logout", AnalyticsLabels.USER_LOGGED_OUT),
    ).toBe("user_logged_out");
  });

  it("returns null for unmapped action", async () => {
    const { resolvePulseEventName } = await import("./pulseRum");
    expect(resolvePulseEventName("unmapped_noise")).toBeNull();
  });
});

describe("trackPulseEvent", () => {
  beforeEach(() => {
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
    mockWhenReady.mockResolvedValue(undefined);
    mockTrackEvent.mockClear();
  });

  it("no-ops when RUM is disabled", async () => {
    const { isPulseRumEnabled } = await import("./pulseRumConfig");
    (isPulseRumEnabled as jest.Mock).mockReturnValue(false);
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({
      action: "anything",
      additionalParams: { [AnalyticsParams.PULSE_EVENT]: "skipped" },
    });

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });

  it("queues when Pulse is not initialized", async () => {
    mockIsInitialized.mockReturnValue(false);
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({
      action: "anything",
      additionalParams: {
        [AnalyticsParams.PULSE_EVENT]: "queued_event",
        foo: "bar",
      },
    });

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });

  it("calls Pulse.trackEvent with enriched attrs when ready", async () => {
    mockIsInitialized.mockReturnValue(true);
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({
      action: "anything",
      additionalParams: {
        [AnalyticsParams.PULSE_EVENT]: "ready_event",
        source: "test",
      },
    });

    expect(mockTrackEvent).toHaveBeenCalledWith("ready_event", {
      tenant_id: "t1",
      source: "test",
    });
  });

  it("forwards mapped login action to user_logged_in", async () => {
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({
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

  it("forwards nav_item_clicked with raw route destination via pulse_event override", async () => {
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({
      action: "menu_click",
      label: "Interactions",
      category: "Navigation",
      additionalParams: {
        [AnalyticsParams.PULSE_EVENT]: "nav_item_clicked",
        destination: "/interactions",
        nav_label: "Interactions",
      },
    });

    expect(mockTrackEvent).toHaveBeenCalledWith(
      "nav_item_clicked",
      expect.objectContaining({
        destination: "/interactions",
        label: "Interactions",
        nav_label: "Interactions",
        category: "Navigation",
      }),
    );
  });

  it("no-ops for unmapped GA action", async () => {
    const { trackPulseEvent } = await import("./pulseRum");

    trackPulseEvent({ action: "random_ga_noise" });

    expect(mockTrackEvent).not.toHaveBeenCalled();
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
    const pulseRum = await import("./pulseRum");

    pulseRum.trackPulseEvent({
      action: "anything",
      additionalParams: {
        [AnalyticsParams.PULSE_EVENT]: "drained",
        x: 1,
      },
    });
    expect(mockTrackEvent).not.toHaveBeenCalled();

    mockIsInitialized.mockReturnValue(true);
    await pulseRum.flushPendingPulseEvents();

    expect(mockWhenReady).toHaveBeenCalled();
    expect(mockTrackEvent).toHaveBeenCalledWith("drained", {
      tenant_id: "t1",
      x: 1,
    });
  });
});

describe("syncPulseUserIdentity", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
    mockIsInitialized.mockReturnValue(false);
    mockWhenReady.mockResolvedValue(undefined);
  });

  it("queues identity and applies after whenReady when SDK is not initialized", async () => {
    const { syncPulseUserIdentity, flushPulseUserIdentityWhenReady } =
      await import("./pulseRum");

    syncPulseUserIdentity({ userId: "user-1", email: "a@b.c" });
    expect(mockSetUserId).not.toHaveBeenCalled();

    mockIsInitialized.mockReturnValue(true);
    await flushPulseUserIdentityWhenReady();

    expect(mockWhenReady).toHaveBeenCalled();
    expect(mockSetUserId).toHaveBeenCalledWith("user-1");
    expect(Pulse.setUserProperties).toHaveBeenCalledWith(
      expect.objectContaining({ email: "a@b.c" }),
    );
  });

  it("applies immediately when SDK is already initialized", async () => {
    mockIsInitialized.mockReturnValue(true);
    const { syncPulseUserIdentity } = await import("./pulseRum");

    syncPulseUserIdentity({ userId: "user-2" });

    expect(mockSetUserId).toHaveBeenCalledWith("user-2");
    expect(mockWhenReady).not.toHaveBeenCalled();
  });
});
