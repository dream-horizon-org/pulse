import {
  isRcaStructuredReportV1WithContent,
  segmentHasDisplayableBody,
  type RcaStructuredReportV1,
  type RcaStructuredSegmentV1,
} from "../useGetRcaReport.interface";

const baseSegment = (): RcaStructuredSegmentV1 => ({
  rank: 1,
  title: "Test segment",
  metrics: [],
});

describe("segmentHasDisplayableBody", () => {
  const opts = (hasProjectForHeatmaps: boolean) => ({
    hasProjectForHeatmaps,
  });

  it("returns false when there is no metrics, narrative, sessions, or heatmaps", () => {
    const segment = baseSegment();
    expect(segmentHasDisplayableBody(segment, opts(true))).toBe(false);
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(false);
  });

  it("returns true when metrics array is non-empty", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      metrics: [
        {
          metric_id: "error_rate",
          metric_label: "Error rate",
          value_display: "1%",
          baseline_display: "0.5%",
          delta_display: "+0.5pp",
          value_number: 0.01,
          baseline_number: 0.005,
        },
      ],
    };
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(true);
  });

  it("returns true for non-empty trimmed impact", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      impact: "  Users see timeouts  ",
    };
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(true);
  });

  it("returns true for non-empty trimmed insights", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      insights: "\nCorrelated with cold start\n",
    };
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(true);
  });

  it("returns false for whitespace-only impact and insights", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      impact: "   \t",
      insights: "",
    };
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(false);
  });

  it("returns true when any affected_sessions id is non-empty after trim", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      affected_sessions: ["", "  ", "abc-123", "  "],
    };
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(true);
  });

  it("returns false when affected_sessions is empty or only blank ids", () => {
    expect(
      segmentHasDisplayableBody(
        { ...baseSegment(), affected_sessions: [] },
        opts(false),
      ),
    ).toBe(false);
    expect(
      segmentHasDisplayableBody(
        { ...baseSegment(), affected_sessions: ["", "  "] },
        opts(false),
      ),
    ).toBe(false);
  });

  it("returns true for related_heatmaps screens only when hasProjectForHeatmaps is true", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      related_heatmaps: { screens: ["  Home  ", ""] },
    };
    expect(segmentHasDisplayableBody(segment, opts(true))).toBe(true);
    expect(segmentHasDisplayableBody(segment, opts(false))).toBe(false);
  });

  it("returns false for heatmap screens that are only whitespace when project is available", () => {
    const segment: RcaStructuredSegmentV1 = {
      ...baseSegment(),
      related_heatmaps: { screens: ["", "  \t"] },
    };
    expect(segmentHasDisplayableBody(segment, opts(true))).toBe(false);
  });

  it("treats null metrics and null optional fields as empty", () => {
    const segment = {
      rank: 2,
      title: "S",
      metrics: null,
      impact: null,
      insights: null,
      affected_sessions: null,
      related_heatmaps: null,
    } as unknown as RcaStructuredSegmentV1;
    expect(segmentHasDisplayableBody(segment, opts(true))).toBe(false);
  });
});

const emptyStructuredReport = (): RcaStructuredReportV1 => ({
  version: 1,
  executive_summary: "",
  segments: [],
  recommendations: [],
});

describe("isRcaStructuredReportV1WithContent", () => {
  it("returns false when only insights have text and relatedAttributions is empty", () => {
    const structured: RcaStructuredReportV1 = {
      ...emptyStructuredReport(),
      error_attribution_insights: [
        {
          signal: "anr",
          summary: null,
          caveat: "Correlative drill-down only.",
        },
        {
          signal: "non_fatal",
          summary: null,
          caveat: "Correlative drill-down only.",
        },
        {
          signal: "api",
          summary: null,
          caveat: "Correlative drill-down only.",
        },
      ],
      error_attribution: {
        disclaimer: "",
        relatedAttributions: [],
      },
    };
    expect(isRcaStructuredReportV1WithContent(structured)).toBe(false);
  });

  it("returns true when relatedAttributions has at least one row", () => {
    const structured: RcaStructuredReportV1 = {
      ...emptyStructuredReport(),
      error_attribution: {
        disclaimer: "",
        relatedAttributions: [
          {
            sourceSignal: "api",
            rowKind: "api",
            url: "https://example.com/graphql",
            occurrences: 12,
          },
        ],
      },
    };
    expect(isRcaStructuredReportV1WithContent(structured)).toBe(true);
  });

  it("returns true for non-empty executive_summary", () => {
    expect(
      isRcaStructuredReportV1WithContent({
        ...emptyStructuredReport(),
        executive_summary: "Overall stable.",
      }),
    ).toBe(true);
  });
});
