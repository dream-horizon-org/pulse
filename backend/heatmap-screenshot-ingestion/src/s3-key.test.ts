import { describe, expect, it } from "vitest";

import {
  buildHeatmapS3ObjectKey,
  buildIngestionS3ObjectTagging,
  sanitizePathSegment,
  utcDateTagYyyyMmDdFromMillis,
} from "./s3-key";

describe("sanitizePathSegment", () => {
  it("replaces slashes and trims", () => {
    expect(sanitizePathSegment("a/b", "fb")).toBe("a_b");
  });
});

describe("buildIngestionS3ObjectTagging", () => {
  it("matches session-replay: project_id + date (yyyy-MM-dd UTC), URL-encoded", () => {
    const ms = Date.UTC(2026, 3, 2, 12, 0, 0);
    const date = utcDateTagYyyyMmDdFromMillis(ms);
    expect(date).toBe("2026-04-02");
    expect(
      buildIngestionS3ObjectTagging("default-project", date),
    ).toBe("project_id=default-project&date=2026-04-02");
    expect(buildIngestionS3ObjectTagging("a/b", date)).toBe(
      "project_id=a%2Fb&date=2026-04-02",
    );
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
