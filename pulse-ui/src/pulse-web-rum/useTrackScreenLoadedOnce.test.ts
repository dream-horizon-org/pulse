import { renderHook } from "@testing-library/react";
import { trackPulseEvent } from "./pulseRumAnalytics";
import { useTrackScreenLoadedOnce } from "./useTrackScreenLoadedOnce";

jest.mock("./pulseRumAnalytics", () => ({
  trackPulseEvent: jest.fn(),
}));

const mockTrackPulseEvent = trackPulseEvent as jest.Mock;

describe("useTrackScreenLoadedOnce", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("fires the event once when ready becomes true", () => {
    const { rerender } = renderHook(
      ({ ready }) =>
        useTrackScreenLoadedOnce({
          eventName: "interactions_list_loaded",
          ready,
        }),
      { initialProps: { ready: false } },
    );

    expect(mockTrackPulseEvent).not.toHaveBeenCalled();

    rerender({ ready: true });
    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(1);
    expect(mockTrackPulseEvent).toHaveBeenCalledWith(
      "interactions_list_loaded",
      undefined,
    );

    rerender({ ready: true });
    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(1);
  });

  it("passes attrs and refires after resetKey changes", () => {
    const attrs = { project_id: "proj-a" };
    const { rerender } = renderHook(
      ({ ready, resetKey }) =>
        useTrackScreenLoadedOnce({
          eventName: "home_loaded",
          attrs,
          ready,
          resetKey,
        }),
      { initialProps: { ready: true, resetKey: "a" } },
    );

    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(1);
    expect(mockTrackPulseEvent).toHaveBeenLastCalledWith("home_loaded", attrs);

    rerender({ ready: true, resetKey: "b" });
    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(2);
  });

  it("does not fire while ready is false after a reset", () => {
    const { rerender } = renderHook(
      ({ ready, resetKey }) =>
        useTrackScreenLoadedOnce({
          eventName: "home_loaded",
          ready,
          resetKey,
        }),
      { initialProps: { ready: true, resetKey: "a" } },
    );

    rerender({ ready: false, resetKey: "b" });
    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(1);

    rerender({ ready: false, resetKey: "b" });
    expect(mockTrackPulseEvent).toHaveBeenCalledTimes(1);
  });
});
