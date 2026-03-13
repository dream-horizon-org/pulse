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
      networkErrors: 2,
      interactionErrors: 1,
      crashCount: 0,
      anrCount: 0,
      nonFatal: 0,
      slowInteractionCount: 1,
      frozenFrameCount: 0,
      platform: "Android",
      spanCount: 34,
      journey: ["/home", "/search", "/contest"],
    },
    {
      sessionId: "sess_mock_002",
      startTime: new Date(now - 4 * 60 * 60 * 1000).toISOString(),
      durationMs: 92000,
      user: null,
      qualityScore: 0.72,
      networkErrors: 0,
      interactionErrors: 0,
      crashCount: 0,
      anrCount: 0,
      nonFatal: 1,
      slowInteractionCount: 0,
      frozenFrameCount: 2,
      platform: "iOS",
      spanCount: 22,
      journey: ["/login", "/home", "/offers"],
    },
    {
      sessionId: "sess_mock_003",
      startTime: new Date(now - 5 * 60 * 60 * 1000).toISOString(),
      durationMs: 310000,
      user: "user_1234",
      qualityScore: 0.95,
      networkErrors: 0,
      interactionErrors: 0,
      crashCount: 0,
      anrCount: 0,
      nonFatal: 0,
      slowInteractionCount: 0,
      frozenFrameCount: 0,
      platform: "Web",
      spanCount: 48,
      journey: ["/home", "/search", "/contest", "/pay", "/wallet"],
    },
    {
      sessionId: "sess_mock_004",
      startTime: new Date(now - 6 * 60 * 60 * 1000).toISOString(),
      durationMs: 45000,
      user: "user_5678",
      qualityScore: null,
      networkErrors: 3,
      interactionErrors: 2,
      crashCount: 1,
      anrCount: 0,
      nonFatal: 2,
      slowInteractionCount: 2,
      frozenFrameCount: 1,
      platform: "Android",
      spanCount: 12,
      journey: ["/home"],
    },
    {
      sessionId: "sess_mock_005",
      startTime: new Date(now - 8 * 60 * 60 * 1000).toISOString(),
      durationMs: 210000,
      user: "user_9012",
      qualityScore: 0.68,
      networkErrors: 1,
      interactionErrors: 1,
      crashCount: 0,
      anrCount: 1,
      nonFatal: 0,
      slowInteractionCount: 3,
      frozenFrameCount: 0,
      platform: "iOS",
      spanCount: 41,
      journey: ["/home", "/profile", "/settings"],
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
