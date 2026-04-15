import { describe, expect, it } from "vitest";

import { buildHeatmapDedupeKey, buildHeatmapQuotaKey } from "./heatmap-redis";

describe("buildHeatmapQuotaKey", () => {
  it("matches S3 segment order (date, project, screen, platform, version, breakpoint)", () => {
    const k = buildHeatmapQuotaKey({
      metaTimestampMs: Date.UTC(2026, 3, 2, 15, 0, 0),
      projectId: "p1",
      screenHref: "/ListFragment",
      platform: "Android",
      appVersionLabel: "1.0.0",
      breakpoint: "Mobile_Small",
    });
    expect(k).toBe(
      "heatmap:quota:20260402:p1:_ListFragment:Android:1.0.0:Mobile_Small",
    );
  });
});

describe("buildHeatmapDedupeKey", () => {
  it("is stable for same inputs", () => {
    const a = buildHeatmapDedupeKey({
      sessionId: "sess-1",
      screenHref: "/Screen",
      metaTimestamp: 12345,
      base64: "YWJj",
    });
    const b = buildHeatmapDedupeKey({
      sessionId: "sess-1",
      screenHref: "/Screen",
      metaTimestamp: 12345,
      base64: "YWJj",
    });
    expect(a).toBe(b);
    expect(a.startsWith("heatmap:dedupe:sess-1:_Screen:12345:")).toBe(true);
  });
});
