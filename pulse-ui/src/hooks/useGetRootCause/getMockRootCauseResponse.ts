import type { RootCauseResponse } from "./useGetRootCause.interface";

/**
 * Mock root cause response for UI development when backend is not ready.
 * Matches the contract: { baseline, segments, cachedAt?, mode? }.
 */
export function getMockRootCauseResponse(
  interactionName: string,
): RootCauseResponse {
  const now = new Date().toISOString();
  return {
    baseline: {
      volume: 3855,
      apdex: 0.56,
      error_rate: 6.9,
      poor_user_pct: 14.7,
      duration_p50: 1041,
      duration_p95: 3238,
      crash_rate: 1.1,
      anr_rate: 1.2,
      frozen_frame_rate: 0.5,
      slow_frame_rate: 3.0,
    },
    segments: [
      {
        label: "Android + App 3.4.5",
        dimensions: { Platform: "android", AppVersion: "3.4.5" },
        metrics: {
          volume: 500,
          apdex: 0.04,
          error_rate: 15.6,
          poor_user_pct: 84.8,
          duration_p50: 2781,
          duration_p95: 3774,
          crash_rate: 4.8,
          anr_rate: 4.8,
          frozen_frame_rate: 2.1,
          slow_frame_rate: 8.2,
        },
        deltas: {
          volume: 13,
          apdex: -93,
          error_rate: 126,
          poor_user_pct: 477,
          duration_p50: 167,
          duration_p95: 16,
          crash_rate: 336,
          anr_rate: 300,
          frozen_frame_rate: 320,
          slow_frame_rate: 173,
        },
      },
      {
        label: "Platform – Android",
        dimensions: { Platform: "android" },
        metrics: {
          volume: 2100,
          apdex: 0.32,
          error_rate: 9.2,
          poor_user_pct: 38.5,
          duration_p50: 1850,
          duration_p95: 3500,
          crash_rate: 2.4,
          anr_rate: 2.1,
          frozen_frame_rate: 1.2,
          slow_frame_rate: 5.5,
        },
        deltas: {
          volume: 54,
          apdex: -43,
          error_rate: 33,
          poor_user_pct: 162,
          duration_p50: 78,
          duration_p95: 8,
          crash_rate: 118,
          anr_rate: 75,
          frozen_frame_rate: 140,
          slow_frame_rate: 83,
        },
      },
    ],
    cachedAt: now,
    mode: "hierarchical",
  };
}
