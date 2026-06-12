import { describe, expect, it } from "vitest";

import { extractHeatmapScreenshots, findFirstScreenshotBase64 } from "./heatmap-extract";
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

describe("extractHeatmapScreenshots", () => {
  it("returns empty when META missing", () => {
    const parsed = mockParsed({
      events: [
        { type: 2, timestamp: 1000, data: { wireframes: [{ type: "screenshot", base64: "x" }] } },
      ],
    });
    expect(extractHeatmapScreenshots(parsed)).toEqual([]);
  });

  it("returns empty when screenshot missing", () => {
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
    expect(extractHeatmapScreenshots(parsed)).toEqual([]);
  });

  it("extracts single META + screenshot pair", () => {
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
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(1);
    expect(all[0]!.meta.href).toBe("ListFragment");
    expect(all[0]!.base64).toBe("AAA");
  });

  it("extracts every ordered META+FULL pair", () => {
    const parsed = mockParsed({
      events: [
        {
          type: 4,
          timestamp: 10,
          data: { href: "A", width: 400, height: 800 },
        },
        {
          type: 2,
          timestamp: 10,
          data: { wireframes: [{ type: "screenshot", base64: "snapA" }] },
        },
        {
          type: 4,
          timestamp: 20,
          data: { href: "B", width: 400, height: 800 },
        },
        {
          type: 2,
          timestamp: 20,
          data: { wireframes: [{ type: "screenshot", base64: "snapB" }] },
        },
      ],
    });
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(2);
    expect(all[0]!.meta.href).toBe("A");
    expect(all[0]!.base64).toBe("snapA");
    expect(all[1]!.meta.href).toBe("B");
    expect(all[1]!.base64).toBe("snapB");
  });

  it("uses latest META when two METAs precede one FULL", () => {
    const parsed = mockParsed({
      events: [
        { type: 4, timestamp: 1, data: { href: "Old", width: 400, height: 800 } },
        { type: 4, timestamp: 2, data: { href: "New", width: 400, height: 800 } },
        {
          type: 2,
          timestamp: 3,
          data: { wireframes: [{ type: "screenshot", base64: "x" }] },
        },
      ],
    });
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(1);
    expect(all[0]!.meta.href).toBe("New");
  });

  it("skips FULL without preceding META", () => {
    const parsed = mockParsed({
      events: [
        {
          type: 2,
          timestamp: 1,
          data: { wireframes: [{ type: "screenshot", base64: "orphan" }] },
        },
        { type: 4, timestamp: 2, data: { href: "S", width: 400, height: 800 } },
        {
          type: 2,
          timestamp: 3,
          data: { wireframes: [{ type: "screenshot", base64: "ok" }] },
        },
      ],
    });
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(1);
    expect(all[0]!.base64).toBe("ok");
  });

  it("does not pair META with FULL when incremental snapshot is between them", () => {
    const parsed = mockParsed({
      events: [
        { type: 4, timestamp: 1, data: { href: "Screen", width: 400, height: 800 } },
        {
          type: 3,
          timestamp: 2,
          data: { source: 0, adds: [], removes: [], updates: [] },
        },
        {
          type: 2,
          timestamp: 3,
          data: { wireframes: [{ type: "screenshot", base64: "full" }] },
        },
      ],
    });
    expect(extractHeatmapScreenshots(parsed)).toEqual([]);
  });

  it("pairs META with FULL after incremental when a new META follows", () => {
    const parsed = mockParsed({
      events: [
        { type: 4, timestamp: 1, data: { href: "Lost", width: 400, height: 800 } },
        {
          type: 3,
          timestamp: 2,
          data: { source: 0, adds: [], removes: [], updates: [] },
        },
        { type: 4, timestamp: 3, data: { href: "Kept", width: 400, height: 800 } },
        {
          type: 2,
          timestamp: 4,
          data: { wireframes: [{ type: "screenshot", base64: "after" }] },
        },
      ],
    });
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(1);
    expect(all[0]!.meta.href).toBe("Kept");
    expect(all[0]!.base64).toBe("after");
  });

  it("clears META when any non-META non-pairable event is between META and FULL (e.g. type 5)", () => {
    const parsed = mockParsed({
      events: [
        { type: 4, timestamp: 1, data: { href: "Screen", width: 400, height: 800 } },
        { type: 5, timestamp: 2, data: { tag: "custom" } },
        {
          type: 2,
          timestamp: 3,
          data: { wireframes: [{ type: "screenshot", base64: "full" }] },
        },
      ],
    });
    expect(extractHeatmapScreenshots(parsed)).toEqual([]);
  });

  it("clears META after full snapshot without screenshot so next META can pair", () => {
    const parsed = mockParsed({
      events: [
        { type: 4, timestamp: 1, data: { href: "First", width: 400, height: 800 } },
        { type: 2, timestamp: 2, data: { wireframes: [{ type: "box", id: 1 }] } },
        { type: 4, timestamp: 3, data: { href: "Second", width: 400, height: 800 } },
        {
          type: 2,
          timestamp: 4,
          data: { wireframes: [{ type: "screenshot", base64: "ok" }] },
        },
      ],
    });
    const all = extractHeatmapScreenshots(parsed);
    expect(all).toHaveLength(1);
    expect(all[0]!.meta.href).toBe("Second");
    expect(all[0]!.base64).toBe("ok");
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
