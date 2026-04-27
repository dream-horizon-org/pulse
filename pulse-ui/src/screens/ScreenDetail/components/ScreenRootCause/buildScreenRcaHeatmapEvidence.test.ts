import {
  buildScreenRcaHeatmapFilters,
  fromDateFromStartInclusiveUtc,
  toDateFromExclusiveEndUtc,
} from "./buildScreenRcaHeatmapEvidence";

describe("buildScreenRcaHeatmapEvidence", () => {
  describe("toDateFromExclusiveEndUtc", () => {
    it("uses UTC calendar date of last instant inside window (end exclusive minus 1ms)", () => {
      expect(toDateFromExclusiveEndUtc("2026-04-08T00:00:00.000Z")).toBe(
        "2026-04-07",
      );
    });

    it("stays on same UTC calendar day when end is mid-day", () => {
      expect(toDateFromExclusiveEndUtc("2026-04-08T15:30:00.000Z")).toBe(
        "2026-04-08",
      );
    });
  });

  describe("fromDateFromStartInclusiveUtc", () => {
    it("uses UTC calendar date of inclusive start", () => {
      expect(fromDateFromStartInclusiveUtc("2026-04-07T10:00:00.000Z")).toBe(
        "2026-04-07",
      );
    });
  });

  describe("buildScreenRcaHeatmapFilters", () => {
    it("maps Platform, AppVersion, GeoState and date range aligned with Java merger", () => {
      const f = buildScreenRcaHeatmapFilters(
        { Platform: "android", AppVersion: "1.2", GeoState: "CA" },
        "2026-01-10T00:00:00.000Z",
        "2026-01-11T00:00:00.000Z",
      );
      expect(f.platform).toBe("android");
      expect(f.app_version).toBe("1.2");
      expect(f.geographical_region).toBe("CA");
      expect(f.breakpoint).toBeNull();
      expect(f.from_date).toBe("2026-01-10");
      expect(f.to_date).toBe("2026-01-10");
    });

    it("nulls missing dimension values", () => {
      const f = buildScreenRcaHeatmapFilters(
        { Platform: "ios" },
        "2026-06-01T00:00:00.000Z",
        "2026-06-03T00:00:00.000Z",
      );
      expect(f.platform).toBe("ios");
      expect(f.app_version).toBeNull();
      expect(f.geographical_region).toBeNull();
    });
  });
});
