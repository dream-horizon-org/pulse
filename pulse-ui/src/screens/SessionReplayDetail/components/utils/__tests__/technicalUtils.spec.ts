import { formatTimestamp, isRecent } from "../technicalUtils";

describe("technicalUtils", () => {
  describe("formatTimestamp", () => {
    it("formats milliseconds under 60 seconds as seconds only", () => {
      expect(formatTimestamp(0)).toBe("0s");
      expect(formatTimestamp(5000)).toBe("5s");
      expect(formatTimestamp(59000)).toBe("59s");
    });

    it("formats 60 seconds and above as minutes and seconds", () => {
      expect(formatTimestamp(60000)).toBe("1m 0s");
      expect(formatTimestamp(90000)).toBe("1m 30s");
      expect(formatTimestamp(125000)).toBe("2m 5s");
      expect(formatTimestamp(3661000)).toBe("61m 1s");
    });
  });

  describe("isRecent", () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it("returns true for dates within the last 7 days", () => {
      jest.setSystemTime(new Date("2025-03-08T12:00:00Z"));
      expect(isRecent("2025-03-05T12:00:00Z")).toBe(true);
      expect(isRecent("2025-03-02T12:00:00Z")).toBe(true);
      expect(isRecent("2025-03-08T11:00:00Z")).toBe(true);
    });

    it("returns false for dates older than 7 days", () => {
      jest.setSystemTime(new Date("2025-03-08T12:00:00Z"));
      expect(isRecent("2025-02-28T12:00:00Z")).toBe(false);
      expect(isRecent("2025-03-01T00:00:00Z")).toBe(false);
    });
  });
});
