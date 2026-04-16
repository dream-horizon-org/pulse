import { describe, expect, it } from "vitest";
import {
  HeatmapBreakpoint,
  HEATMAP_PLATFORM_WEB,
  resolveHeatmapBreakpoint,
} from "./breakpoint-rules";

type Case = {
  name: string;
  platform: string;
  width: number;
  height: number;
  want: string;
};

describe("resolveHeatmapBreakpoint", () => {
  const cases: Case[] = [
    // Web — first branch
    {
      name: "web width 1025 → Web_Extra_Large",
      platform: HEATMAP_PLATFORM_WEB,
      width: 1025,
      height: 800,
      want: HeatmapBreakpoint.Web_Extra_Large,
    },
    {
      name: "web large desktop",
      platform: HEATMAP_PLATFORM_WEB,
      width: 1920,
      height: 1080,
      want: HeatmapBreakpoint.Web_Extra_Large,
    },
    // Web — second branch (all web ≤1024 including tablet-sized css pixels)
    {
      name: "web width 1024 → Mobile_Medium (not tablet)",
      platform: HEATMAP_PLATFORM_WEB,
      width: 1024,
      height: 768,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    {
      name: "web width 800 → Mobile_Medium (conflicts with flat >600 row; Web wins)",
      platform: HEATMAP_PLATFORM_WEB,
      width: 800,
      height: 1200,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    {
      name: "web narrow",
      platform: HEATMAP_PLATFORM_WEB,
      width: 390,
      height: 844,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    // Native — tablet
    {
      name: "android tablet width 601",
      platform: "Android",
      width: 601,
      height: 800,
      want: HeatmapBreakpoint.Tablet_Large,
    },
    {
      name: "ios tablet width 800",
      platform: "iOS",
      width: 800,
      height: 600,
      want: HeatmapBreakpoint.Tablet_Large,
    },
    // Native — phone small
    {
      name: "phone width 600 aspect at boundary 1.5",
      platform: "Android",
      width: 600,
      height: 900,
      want: HeatmapBreakpoint.Mobile_Small,
    },
    {
      name: "phone narrow short aspect",
      platform: "Android",
      width: 400,
      height: 500,
      want: HeatmapBreakpoint.Mobile_Small,
    },
    // Native — phone medium (tall)
    {
      name: "phone width 600 tall foldable aspect > 1.5",
      platform: "Android",
      width: 600,
      height: 901,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    {
      name: "phone width 400 tall",
      platform: "Android",
      width: 400,
      height: 900,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    // Boundaries
    {
      name: "native width 600 exactly → aspect branch",
      platform: "Android",
      width: 600,
      height: 901,
      want: HeatmapBreakpoint.Mobile_Medium,
    },
    {
      name: "native width 601 exactly → tablet",
      platform: "Android",
      width: 601,
      height: 600,
      want: HeatmapBreakpoint.Tablet_Large,
    },
  ];

  it.each(cases)("$name", ({ platform, width, height, want }) => {
    expect(resolveHeatmapBreakpoint(platform, width, height)).toBe(want);
  });

  it("rejects non-positive width", () => {
    expect(() => resolveHeatmapBreakpoint("Android", 0, 800)).toThrow(RangeError);
    expect(() => resolveHeatmapBreakpoint("Android", -1, 800)).toThrow(RangeError);
  });

  it("rejects non-finite dimensions", () => {
    expect(() => resolveHeatmapBreakpoint("Android", NaN, 800)).toThrow(RangeError);
    expect(() => resolveHeatmapBreakpoint("Android", 400, Infinity)).toThrow(RangeError);
  });
});
