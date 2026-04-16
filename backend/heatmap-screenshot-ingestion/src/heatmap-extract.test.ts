import { describe, expect, it } from "vitest";

import { extractHeatmapScreenshot, findFirstScreenshotBase64 } from "./heatmap-extract";
import type { ParsedMessageData } from "./kafka/types";
import { DateTime } from "luxon";

describe("findFirstScreenshotBase64", () => {
  it("finds nested screenshot", () => {
    const wf = [
      { id: 1, type: "box", childWireframes: [{ id: 2, type: "screenshot", base64: "abc" }] },
    ];
    expect(findFirstScreenshotBase64(wf)).toBe("abc");
  });
});

describe("extractHeatmapScreenshot", () => {
  it("returns null when META missing", () => {
    const parsed = mockParsed({
      events: [
        { type: 2, timestamp: 1000, data: { wireframes: [{ type: "screenshot", base64: "x" }] } },
      ],
    });
    expect(extractHeatmapScreenshot(parsed)).toBeNull();
  });

  it("returns null when screenshot missing", () => {
    const parsed = mockParsed({
      events: [
        {
          type: 4,
          timestamp: 1000,
          data: { href: "S", width: 400, height: 800 },
        },
        { type: 2, timestamp: 1001, data: { wireframes: [{ type: "box", id: 1 }] } },
      ],
    });
    expect(extractHeatmapScreenshot(parsed)).toBeNull();
  });

  it("extracts META + screenshot", () => {
    const parsed = mockParsed({
      events: [
        {
          type: 4,
          timestamp: 1000,
          data: { href: "ListFragment", width: 400, height: 900 },
        },
        {
          type: 2,
          timestamp: 1001,
          data: {
            wireframes: [{ id: 1, type: "screenshot", base64: "AAA" }],
            initialOffset: { top: 0, left: 0 },
          },
        },
      ],
    });
    const r = extractHeatmapScreenshot(parsed);
    expect(r).not.toBeNull();
    expect(r!.meta.href).toBe("ListFragment");
    expect(r!.base64).toBe("AAA");
  });
});

function mockParsed(partial: Partial<ParsedMessageData>): ParsedMessageData {
  const start = DateTime.fromMillis(1000);
  return {
    user_id: "u",
    session_id: "s",
    project_id: "p",
    app_version: "1.0",
    events: partial.events ?? [],
    eventsRange: { start, end: start },
    snapshot_source: "Android",
    metadata: {
      partition: 0,
      topic: "t",
      rawSize: 1,
      offset: 0,
      timestamp: 1,
    },
  };
}
