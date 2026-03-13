/**
 * Session Replay mock for listing and detail.
 * Used when REACT_APP_USE_MOCK_SESSION_REPLAY=true so session list and detail
 * use mock data while the rest of the app uses the real API.
 */

import type {
  SessionListingRequest,
  SessionListingResponse,
  SessionItem,
  SessionDetailApiResponse,
} from "../../../services/sessionReplay/types";
import { generateSessionDetailApiResponse } from "../../../mocks/responses/sessionReplayResponses";

function getMockSessionItems(): SessionItem[] {
  const now = Date.now();
  return [
    {
      sessionId: "sess_mock_001",
      startTime: new Date(now - 2 * 60 * 60 * 1000).toISOString(),
      durationMs: 159000,
      user: "user_3456",
      qualityScore: 0.86,
      issues: [
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 1 },
      ],
      platform: "Android",
      spanCount: 34,
      journey: ["/home", "/search", "/contest"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_002",
      startTime: new Date(now - 4 * 60 * 60 * 1000).toISOString(),
      durationMs: 92000,
      user: null,
      qualityScore: 0.72,
      issues: [
        { type: "NON_FATAL", label: "Non-Fatals", count: 1 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 2 },
      ],
      platform: "iOS",
      spanCount: 22,
      journey: ["/login", "/home", "/offers"],
      impactedScreens: { nonFatals: ["/offers"] },
    },
    {
      sessionId: "sess_mock_003",
      startTime: new Date(now - 5 * 60 * 60 * 1000).toISOString(),
      durationMs: 310000,
      user: "user_1234",
      qualityScore: 0.95,
      issues: [],
      platform: "Web",
      spanCount: 48,
      journey: ["/home", "/search", "/contest", "/pay", "/wallet"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_004",
      startTime: new Date(now - 6 * 60 * 60 * 1000).toISOString(),
      durationMs: 45000,
      user: "user_5678",
      qualityScore: null,
      issues: [
        { type: "CRASH", label: "Crashes", count: 1 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 3 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 2 },
        { type: "NON_FATAL", label: "Non-Fatals", count: 2 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 2 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 1 },
      ],
      platform: "Android",
      spanCount: 12,
      journey: ["/home"],
      impactedScreens: {
        crashes: ["/home"],
        nonFatals: ["/home"],
      },
    },
    {
      sessionId: "sess_mock_005",
      startTime: new Date(now - 8 * 60 * 60 * 1000).toISOString(),
      durationMs: 210000,
      user: "user_9012",
      qualityScore: 0.68,
      issues: [
        { type: "ANR", label: "ANRs", count: 1 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 1 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 3 },
      ],
      platform: "iOS",
      spanCount: 41,
      journey: ["/home", "/profile", "/settings"],
      impactedScreens: { anrs: ["/profile"] },
    },
  ];
}

/**
 * Returns mock session listing response (same contract as POST /v1/sessions/listing).
 */
export function getMockSessionListingResponse(
  request: SessionListingRequest,
): SessionListingResponse {
  const limit = Math.min(100, Math.max(1, request.page?.limit ?? 10));
  const cursor = request.page?.cursor;

  const allSessions = getMockSessionItems();
  let startIndex = 0;
  if (cursor && typeof atob === "function") {
    try {
      const decoded = JSON.parse(decodeURIComponent(atob(cursor)));
      startIndex = Number(decoded.offset) || 0;
    } catch {
      startIndex = 0;
    }
  }
  const slice = allSessions.slice(startIndex, startIndex + limit + 1);
  const hasMore = slice.length > limit;
  const sessions = slice.slice(0, limit);
  const nextCursor =
    hasMore && sessions.length > 0 && typeof btoa === "function"
      ? btoa(
          encodeURIComponent(
            JSON.stringify({
              offset: startIndex + limit,
              lastId: sessions[sessions.length - 1]?.sessionId,
            }),
          ),
        )
      : null;

  return {
    sessions,
    page: { limit, nextCursor, hasMore },
  };
}

/**
 * Returns mock session detail API response (same contract as GET /v1/session-replay/sessions/:id).
 */
export function getMockSessionDetailApiResponse(
  sessionId: string,
): SessionDetailApiResponse {
  return generateSessionDetailApiResponse(sessionId);
}
