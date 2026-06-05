import { resolveFunnelRcaWindow } from "./FunnelJourneyCreate.util";

describe("resolveFunnelRcaWindow", () => {
  it("uses dateRangeDays for AUTO funnels without startTime/endTime", () => {
    const result = resolveFunnelRcaWindow({
      funnelType: "AUTO",
      dateRangeDays: 7,
    });

    expect(result.windowStartIso).not.toBe("");
    expect(result.windowEndIso).not.toBe("");
    expect(new Date(result.windowEndIso).getTime()).toBeGreaterThan(
      new Date(result.windowStartIso).getTime(),
    );
  });

  it("prefers explicit startTime and endTime when present", () => {
    const result = resolveFunnelRcaWindow({
      funnelType: "AUTO",
      dateRangeDays: 7,
      startTime: "2025-05-01T00:00:00.000Z",
      endTime: "2025-05-08T00:00:00.000Z",
    });

    expect(result.windowStartIso).toBe("2025-05-01T00:00:00.000Z");
    expect(result.windowEndIso).toBe("2025-05-08T00:00:00.000Z");
  });
});
