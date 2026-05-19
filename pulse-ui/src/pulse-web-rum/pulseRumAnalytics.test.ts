import { Pulse } from "@dreamhorizonorg/pulse-web";
import { PULSE_NAV_ROUTES } from "./pulseRumConstants";

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
  withPulseEventContext: jest.fn(
    (attrs?: Record<string, unknown>) => attrs ?? {},
  ),
}));

const mockIsInitialized = Pulse.isInitialized as jest.Mock;
const mockWhenReady = Pulse.whenReady as jest.Mock;
const mockSetUserId = Pulse.setUserId as jest.Mock;

describe("trackPulseEvent", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
  });

  it("strips null and undefined attributes before trackEvent", async () => {
    const { trackPulseEvent } = await import("./pulseRumAnalytics");

    trackPulseEvent("test_event", {
      keep: "yes",
      dropNull: null,
      dropUndef: undefined,
      count: 1,
      flag: false,
    });

    expect(mockTrackEvent).toHaveBeenCalledWith("test_event", {
      keep: "yes",
      count: 1,
      flag: false,
    });
  });

  it("omits attributes when all values are nullish", async () => {
    const { trackPulseEvent } = await import("./pulseRumAnalytics");

    trackPulseEvent("empty_attrs", { onlyNull: null, onlyUndef: undefined });

    expect(mockTrackEvent).toHaveBeenCalledWith("empty_attrs", undefined);
  });

  it("no-ops when RUM is disabled", async () => {
    const { isPulseRumEnabled } = await import("./pulseRumConfig");
    (isPulseRumEnabled as jest.Mock).mockReturnValue(false);
    const { trackPulseEvent } = await import("./pulseRumAnalytics");

    trackPulseEvent("skipped");

    expect(mockTrackEvent).not.toHaveBeenCalled();
  });
});

describe("trackNavItemClicked", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
    mockIsInitialized.mockReturnValue(true);
  });

  it("maps known navbar routes to stable destination slugs", async () => {
    const { trackNavItemClicked } = await import("./pulseRumAnalytics");

    trackNavItemClicked(PULSE_NAV_ROUTES.CRITICAL_INTERACTIONS, "Interactions");

    expect(mockTrackEvent).toHaveBeenCalledWith(
      "nav_item_clicked",
      expect.objectContaining({
        destination: "interactions",
        nav_label: "Interactions",
      }),
    );
  });

  it("falls back to raw route when destination is unknown", async () => {
    const { trackNavItemClicked } = await import("./pulseRumAnalytics");

    trackNavItemClicked("/custom-route");

    expect(mockTrackEvent).toHaveBeenCalledWith(
      "nav_item_clicked",
      expect.objectContaining({
        destination: "/custom-route",
      }),
    );
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
      await import("./pulseRumAnalytics");

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
    const { syncPulseUserIdentity } = await import("./pulseRumAnalytics");

    syncPulseUserIdentity({ userId: "user-2" });

    expect(mockSetUserId).toHaveBeenCalledWith("user-2");
    expect(mockWhenReady).not.toHaveBeenCalled();
  });
});
