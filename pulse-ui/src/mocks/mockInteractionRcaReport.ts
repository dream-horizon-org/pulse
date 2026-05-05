import type { ApiResponse } from "../helpers/makeRequest/makeRequest.interface";
import type {
  RcaJobResponse,
  RcaReportResponse,
  RcaStructuredReportV1,
} from "../hooks/useGetRcaReport/useGetRcaReport.interface";

/** Fixed instant so stale-cache polling agrees with the POST 200 payload (no false “stale” banners). */
export const MOCK_INTERACTION_RCA_CACHED_AT_ISO = "2026-01-15T12:00:00.000Z";

export function buildMockInteractionRcaStructuredReport(
  entityKey: string,
): RcaStructuredReportV1 {
  const label = entityKey.trim() || "interaction";
  return {
    version: 1,
    executive_summary: `Mock RCA for ${label}: checkout-related requests show elevated latency and errors versus baseline in this window (demo only).`,
    segments: [
      {
        rank: 1,
        title: "Payment path degradation",
        impact:
          "Users saw slower confirmations and occasional failures at pay.",
        insights:
          "Mock insight: volume and poor_apdex rise together on PaymentSubmitClick.",
        metrics: [
          {
            metric_id: "volume",
            metric_label: "Sessions",
            value_display: "1.2k",
            baseline_display: "890",
            delta_display: "+35%",
            value_number: 1200,
            baseline_number: 890,
          },
          {
            metric_id: "apdex",
            metric_label: "Apdex",
            value_display: "0.61",
            baseline_display: "0.84",
            delta_display: "-27%",
            value_number: 0.61,
            baseline_number: 0.84,
          },
          {
            metric_id: "error_rate",
            metric_label: "Error rate",
            value_display: "4.2%",
            baseline_display: "1.1%",
            delta_display: "+3.1pp",
            value_number: 0.042,
            baseline_number: 0.011,
          },
        ],
        affected_sessions: ["sess_mock_001"],
        related_heatmaps: {
          screens: ["/checkout", "/payment"],
          heatmap_filters: {
            platform: "Android",
            app_version: "4.0.0",
            from_date: "2026-01-10",
            to_date: "2026-01-15",
          },
        },
      },
      {
        rank: 2,
        title: "Upstream API timeouts",
        impact: "Retry storms amplified perceived slowness.",
        insights:
          "Mock insight: 504s cluster on payment gateway during peak minutes.",
        metrics: [
          {
            metric_id: "duration_p95",
            metric_label: "p95 duration",
            value_display: "4.8s",
            baseline_display: "1.9s",
            delta_display: "+2.9s",
            value_number: 4800,
            baseline_number: 1900,
          },
        ],
      },
    ],
    recommendations: [
      "Add backoff and idempotency keys around payment submission (mock).",
      "Raise client read timeout for the payment API by ~500ms while tuning server SLAs (mock).",
    ],
    error_attribution_insights: [
      {
        signal: "api",
        summary:
          "POST /api/v1/payment intermittently returns 504 during peak traffic.",
        caveat: "Demonstration data — not tied to live telemetry.",
      },
    ],
  };
}

export function getMockInteractionRcaReportApiResponse(
  entityKey: string,
): ApiResponse<RcaReportResponse> {
  return {
    status: 200,
    error: null,
    data: {
      cached: true,
      cachedAt: MOCK_INTERACTION_RCA_CACHED_AT_ISO,
      report: {
        structured: buildMockInteractionRcaStructuredReport(entityKey),
      },
    },
  };
}

/** Matches GET /v1/ai-rca/status shape used by stale-cache polling while a report is displayed. */
export function getMockInteractionRcaStaleStatusApiResponse(): ApiResponse<RcaJobResponse> {
  return {
    status: 200,
    error: null,
    data: {
      jobId: "mock-rca-stale-status",
      status: "COMPLETED",
      createdAt: MOCK_INTERACTION_RCA_CACHED_AT_ISO,
      completedAt: MOCK_INTERACTION_RCA_CACHED_AT_ISO,
      cachedAt: MOCK_INTERACTION_RCA_CACHED_AT_ISO,
      cached: true,
    },
  };
}
