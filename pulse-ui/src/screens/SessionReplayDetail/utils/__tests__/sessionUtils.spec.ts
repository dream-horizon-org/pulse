import {
  formatDuration,
  formatTimestamp,
  formatPlayerTime,
  getQualityColor,
} from "../sessionUtils";

describe("sessionUtils", () => {
  describe("formatDuration", () => {
    it("formats milliseconds as ms when under 1000", () => {
      expect(formatDuration(500)).toBe("500ms");
      expect(formatDuration(0)).toBe("0ms");
    });

    it("formats seconds when under 60000", () => {
      expect(formatDuration(1000)).toBe("1.0s");
      expect(formatDuration(5500)).toBe("5.5s");
      expect(formatDuration(30000)).toBe("30.0s");
    });

    it("formats minutes and seconds when 60000 or more", () => {
      expect(formatDuration(60000)).toBe("1m 0s");
      expect(formatDuration(90000)).toBe("1m 30s");
      expect(formatDuration(154000)).toBe("2m 34s");
      expect(formatDuration(3661000)).toBe("61m 1s");
    });
  });

  describe("formatTimestamp", () => {
    it("formats ms offset relative to session start as HH:mm:ss.SSS", () => {
      const sessionStart = new Date("2025-03-08T14:00:00.000Z");
      const t0 = formatTimestamp(0, sessionStart);
      const t5 = formatTimestamp(5000, sessionStart);
      const t65 = formatTimestamp(65000, sessionStart);
      expect(t0).toMatch(/^\d{2}:\d{2}:\d{2}\.\d{3}$/);
      expect(t5).toMatch(/^\d{2}:\d{2}:\d{2}\.\d{3}$/);
      expect(t65).toMatch(/^\d{2}:\d{2}:\d{2}\.\d{3}$/);
      expect(t0).not.toBe(t5);
      expect(t5).not.toBe(t65);
    });
  });

  describe("formatPlayerTime", () => {
    it("formats ms as MM:SS with zero padding", () => {
      expect(formatPlayerTime(0)).toBe("00:00");
      expect(formatPlayerTime(5000)).toBe("00:05");
      expect(formatPlayerTime(65000)).toBe("01:05");
      expect(formatPlayerTime(125000)).toBe("02:05");
      expect(formatPlayerTime(3661000)).toBe("61:01");
    });

    it("floors to whole seconds", () => {
      expect(formatPlayerTime(5999)).toBe("00:05");
      expect(formatPlayerTime(6001)).toBe("00:06");
    });
  });

  describe("getQualityColor", () => {
    it("returns teal for score >= 8", () => {
      expect(getQualityColor(8)).toBe("teal");
      expect(getQualityColor(8.5)).toBe("teal");
      expect(getQualityColor(10)).toBe("teal");
    });

    it("returns yellow for score >= 6 and < 8", () => {
      expect(getQualityColor(6)).toBe("yellow");
      expect(getQualityColor(7)).toBe("yellow");
      expect(getQualityColor(7.9)).toBe("yellow");
    });

    it("returns red for score < 6", () => {
      expect(getQualityColor(0)).toBe("red");
      expect(getQualityColor(5)).toBe("red");
      expect(getQualityColor(5.9)).toBe("red");
    });
  });
});
