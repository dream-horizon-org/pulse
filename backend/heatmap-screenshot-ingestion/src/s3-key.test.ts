import { describe, expect, it } from "vitest";

import { buildHeatmapS3ObjectKey, sanitizePathSegment } from "./s3-key";

describe("sanitizePathSegment", () => {
  it("replaces slashes and trims", () => {
    expect(sanitizePathSegment("a/b", "fb")).toBe("a_b");
  });
});

describe("buildHeatmapS3ObjectKey", () => {
  it("builds path project → date → screen → platform → appVersion → breakpoint → file", () => {
    const key = buildHeatmapS3ObjectKey({
      s3Prefix: "heatmap-screenshots",
      projectId: "default-project",
      metaTimestampMs: Date.UTC(2026, 3, 2, 12, 0, 0),
      platform: "Android",
      appVersionLabel: "1.0.0",
      screenHref: "ListFragment",
      breakpoint: "Mobile_Small",
      objectFileName: "capture-x.json",
    });
    expect(key).toBe(
      "heatmap-screenshots/default-project/20260402/ListFragment/Android/1.0.0/Mobile_Small/capture-x.json",
    );
  });
});
