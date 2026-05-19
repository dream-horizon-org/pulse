/**
 * Mock handlers for Interaction / Screen / Session RCA (async job + tabular + narrative).
 * Shapes align with pulse-server / pulse_ai JSON used by {@link useGetRcaReport}, screen root cause, and screen narrative hooks.
 */

import type { MockRequest, MockResponse } from "../types";

const MOCK_CACHED_AT = "2025-06-01T12:00:00.000Z";

function tryParseBody(request: MockRequest): Record<string, unknown> | null {
  const raw = request.body;
  if (raw == null || String(raw).trim() === "") {
    return null;
  }
  try {
    const o = JSON.parse(String(raw)) as unknown;
    return o != null && typeof o === "object" && !Array.isArray(o)
      ? (o as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/** Tabular shape (mirrors {@code RootCauseRestResponse}). */
function mockTabularPayload(): Record<string, unknown> {
  return {
    baseline: { volume: 1200, users: 420 },
    segments: [
      {
        label: "Android",
        dimensions: { platform: "Android" },
        metrics: { error_rate: 0.012, apdex: 0.82 },
        deltas: { error_rate: 0.003, apdex: -0.05 },
      },
    ],
    mode: "hierarchical",
    cachedAt: MOCK_CACHED_AT,
    everythingGood: false,
    noDataAvailable: false,
    message: null,
  };
}

function mockInteractionStructuredReport(): Record<string, unknown> {
  return {
    version: 1,
    executive_summary:
      "Mock interaction RCA — enable a real backend or turn off REACT_APP_USE_MOCK_SERVER for live data.",
    segments: [
      {
        rank: 1,
        title: "Primary driver (mock)",
        impact: "moderate",
        insights: "Segment-level insight text for mock development.",
        metrics: [
          {
            metric_id: "volume",
            metric_label: "Volume",
            value_display: "1.2k",
            baseline_display: "1.0k",
            delta_display: "+20%",
            value_number: 1200,
            baseline_number: 1000,
          },
        ],
      },
    ],
    recommendations: [
      "Review top segment in production with real ClickHouse data.",
    ],
  };
}

function mockSessionStructuredReport(): Record<string, unknown> {
  return {
    version: 1,
    executive_summary:
      "Mock session quality RCA — summarizes poor sessions in the selected window (mock).",
    segments: [
      {
        rank: 1,
        title: "Sessions with degraded experience (mock)",
        impact: "critical",
        insights:
          "Mock insight: investigate session_score and degrading flows below.",
        affected_sessions: ["mock-session-a", "mock-session-b"],
        degrading_interactions: [
          {
            interactionName: "CheckoutStart",
            interactionCount: 42,
            avgApdex: 0.35,
            degradationWeight: 12.5,
          },
        ],
        metrics: [
          {
            metric_id: "session_score",
            metric_label: "Session score",
            value_display: "0.42",
            baseline_display: "0.72",
            delta_display: "-42%",
            value_number: 0.42,
            baseline_number: 0.72,
          },
        ],
      },
    ],
    recommendations: [
      "Open session replay for affected sessions when using a real backend.",
    ],
  };
}

function pickStructuredReport(
  body: Record<string, unknown> | null,
): Record<string, unknown> {
  const rcaType = body != null ? String(body.rcaType ?? "").toUpperCase() : "";
  const entityKey = body != null ? String(body.entityKey ?? "") : "";
  if (rcaType === "SESSION" || entityKey === "__session__") {
    return mockSessionStructuredReport();
  }
  return mockInteractionStructuredReport();
}

/** Completed async job / peek payload (inner `data` when wrapped). */
function mockCompletedJobPayload(options: {
  jobId: string;
  structured: Record<string, unknown>;
}): Record<string, unknown> {
  const tabular = mockTabularPayload();
  const report: Record<string, unknown> = {
    structured: options.structured,
    rootCausePayload: tabular,
  };
  return {
    jobId: options.jobId,
    status: "COMPLETED",
    report,
    cached: true,
    cachedAt: MOCK_CACHED_AT,
    createdAt: MOCK_CACHED_AT,
    completedAt: MOCK_CACHED_AT,
  };
}

/** POST 200 RCA report response body when the client uses {@code unwrapped: true}. */
function mockCompletedReportPostPayload(
  structured: Record<string, unknown>,
): Record<string, unknown> {
  return {
    report: {
      structured,
      rootCausePayload: mockTabularPayload(),
    },
    cached: true,
    cachedAt: MOCK_CACHED_AT,
  };
}

function mockScreenNarrativePayload(): Record<string, unknown> {
  return {
    report: {
      narrative: {
        version: 1,
        executive_summary:
          "Mock screen RCA narrative — wire to pulse-server for ClickHouse-backed screen root cause.",
        recommendations: [
          "Validate screen funnel and heatmaps against production traffic.",
        ],
      },
    },
    cached: true,
    cachedAt: MOCK_CACHED_AT,
  };
}

/**
 * Routes RCA-related API paths. Returns `null` when the request should fall through to other handlers.
 */
export function handleRcaMockEndpoints(
  pathname: string,
  method: string,
  request: MockRequest,
): MockResponse | null {
  const upper = method.toUpperCase();

  const isAiRcaReportPath =
    pathname === "/v1/ai/rca/report" || pathname.endsWith("/v1/ai/rca/report");
  const isAiRcaScreenPath =
    pathname === "/v1/ai/rca/screen-report" ||
    pathname.endsWith("/v1/ai/rca/screen-report");
  const isAiRcaReportPeek =
    pathname === "/v1/ai-rca/report" || pathname.endsWith("/v1/ai-rca/report");

  const jobMatch = pathname.match(/\/v1\/ai-rca\/job\/([^/]+)$/);

  const screenRootCauseMatch = pathname.match(
    /^\/v1\/screens\/(.+)\/root-cause$/,
  );
  const interactionRootCauseMatch = pathname.match(
    /^\/v1\/interactions\/(.+)\/root-cause$/,
  );

  if (isAiRcaReportPeek && upper === "GET") {
    const url = new URL(
      request.url,
      typeof window !== "undefined"
        ? window.location.origin
        : "http://localhost",
    );
    const rawType = url.searchParams.get("rcaType") ?? "INTERACTION";
    const entityKey = url.searchParams.get("entityKey") ?? "mock-interaction";
    const bodyLike: Record<string, unknown> = {
      rcaType: rawType,
      entityKey,
    };
    const structured = pickStructuredReport(bodyLike);
    return {
      status: 200,
      data: mockCompletedJobPayload({
        jobId: "mock-rca-peek-cache",
        structured,
      }),
    };
  }

  if (jobMatch && upper === "GET") {
    const jobId = decodeURIComponent(jobMatch[1]);
    const structured = mockInteractionStructuredReport();
    return {
      status: 200,
      data: mockCompletedJobPayload({ jobId, structured }),
    };
  }

  if (isAiRcaReportPath && upper === "POST") {
    const body = tryParseBody(request);
    const structured = pickStructuredReport(body);
    return {
      status: 200,
      data: mockCompletedReportPostPayload(structured),
    };
  }

  if (isAiRcaScreenPath && upper === "POST") {
    return {
      status: 200,
      data: mockScreenNarrativePayload(),
    };
  }

  if (screenRootCauseMatch && upper === "GET") {
    return {
      status: 200,
      data: mockTabularPayload(),
    };
  }

  if (interactionRootCauseMatch && upper === "GET") {
    return {
      status: 200,
      data: mockTabularPayload(),
    };
  }

  return null;
}
