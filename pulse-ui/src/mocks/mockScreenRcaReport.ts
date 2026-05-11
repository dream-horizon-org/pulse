import type { ApiResponse } from "../helpers/makeRequest/makeRequest.interface";
import type { ScreenRootCauseData } from "../hooks/useGetScreenRootCause/useGetScreenRootCause.interface";
import type { ScreenRcaReportApiResponse } from "../hooks/useGetScreenRcaNarrative/useGetScreenRcaNarrative.interface";

/** Tabular GET cache instant — narrative mock uses same so “Report as of” stays stable. */
export const MOCK_SCREEN_RCA_CACHED_AT_ISO = "2026-01-15T14:30:00.000Z";

export function buildMockScreenRootCauseData(
  screenName: string,
): ScreenRootCauseData {
  const label = screenName.trim() || "/home";
  return {
    cachedAt: MOCK_SCREEN_RCA_CACHED_AT_ISO,
    noDataAvailable: false,
    everythingGood: false,
    mode: "flat",
    message: null,
    baseline: {
      click_volume: 18420,
      tap_count: 16400,
      rage_count: 120,
      dead_count: 340,
      bad_frustration: 890,
    },
    segments: [
      {
        label: `High friction — ${label}`,
        dimensions: {
          platform: "Android",
          app_version: "4.0.0",
        },
        metrics: {
          click_volume: 4200,
          tap_count: 3800,
          rage_count: 48,
          dead_count: 112,
          bad_frustration: 260,
        },
        deltas: {
          click_volume: 18.5,
          tap_count: 16.2,
          rage_count: 42.0,
          dead_count: 28.0,
          bad_frustration: 31.0,
        },
        affected_sessions: ["sess_mock_001"],
      },
      {
        label: `Secondary slice — ${label}`,
        dimensions: {
          platform: "iOS",
          app_version: "4.2.0",
        },
        metrics: {
          click_volume: 2100,
          tap_count: 1920,
          rage_count: 12,
          dead_count: 54,
          bad_frustration: 88,
        },
        deltas: {
          click_volume: -4.2,
          tap_count: -3.1,
          rage_count: 8.0,
          dead_count: 11.0,
          bad_frustration: 6.5,
        },
        affected_sessions: ["sess_mock_002"],
      },
    ],
  };
}

export function getMockScreenRootCauseApiResponse(
  screenName: string,
): ApiResponse<ScreenRootCauseData> {
  return {
    status: 200,
    error: null,
    data: buildMockScreenRootCauseData(screenName),
  };
}

export function getMockScreenRcaNarrativeApiResponse(
  screenName: string,
): ApiResponse<ScreenRcaReportApiResponse> {
  const label = screenName.trim() || "this screen";
  return {
    status: 200,
    error: null,
    data: {
      cached: true,
      cachedAt: MOCK_SCREEN_RCA_CACHED_AT_ISO,
      report: {
        narrative: {
          version: 1,
          executive_summary: `Mock narrative for ${label}: tap and rage counts are elevated on Android 4.0.0 versus baseline, with dead clicks clustering on the primary CTA region (demo data only).`,
          recommendations: [
            "Audit layout hit targets on the primary action for smaller breakpoints (mock).",
            "Compare gesture traces for rage taps against navigation timing on checkout (mock).",
          ],
        },
      },
    },
  };
}
