/**
 * Session Replay mock for listing, detail, and snapshots.
 * Used when REACT_APP_USE_MOCK_SESSION_REPLAY=true so session list, detail,
 * and snapshots-data API use mock data in the same contract as the real API.
 */

import type {
  SessionListingRequest,
  SessionListingResponse,
  SessionItem,
  SessionDetailApiResponse,
  FilterConfigResponse,
} from "../../../services/sessionReplay/types";
import type { SnapshotsDataResponse } from "../../../services/sessionReplay/sessionReplaySnapshotTypes";
import { generateSessionDetailApiResponse } from "../../../mocks/responses/sessionReplayResponses";

function getMockSessionItems(): SessionItem[] {
  const now = Date.now();
  /** Staggered start times across ~24h for a realistic “Last 24 hours” table */
  const t = (hoursAgo: number, minutesOffset = 0) =>
    new Date(
      now - hoursAgo * 60 * 60 * 1000 - minutesOffset * 60 * 1000,
    ).toISOString();

  return [
    {
      sessionId: "sess_mock_001",
      startTime: t(0, 17),
      durationMs: 159000,
      user: "user_3456",
      qualityScore: 0.86,
      issues: [
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 1 },
      ],
      platform: "Android",
      spanCount: 187,
      journey: ["/home", "/search", "/contest", "/checkout"],
      impactedScreens: { nonFatals: ["/search"] },
    },
    {
      sessionId: "sess_mock_002",
      startTime: t(2, 0),
      durationMs: 92000,
      user: null,
      qualityScore: 0.72,
      issues: [
        { type: "NON_FATAL", label: "Non-Fatals", count: 1 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 2 },
      ],
      platform: "iOS",
      spanCount: 94,
      journey: ["/login", "/home", "/offers", "/cart"],
      impactedScreens: { nonFatals: ["/offers"] },
    },
    {
      sessionId: "sess_mock_003",
      startTime: t(3, 0),
      durationMs: 310000,
      user: "user_1234",
      qualityScore: 0.95,
      issues: [],
      platform: "Web",
      spanCount: 256,
      journey: ["/home", "/search", "/contest", "/pay", "/wallet", "/receipt"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_004",
      startTime: t(4, 0),
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
      spanCount: 61,
      journey: ["/splash", "/home", "/feed"],
      impactedScreens: {
        crashes: ["/home"],
        nonFatals: ["/home", "/feed", "/settings"],
      },
    },
    {
      sessionId: "sess_mock_005",
      startTime: t(6, 0),
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
      spanCount: 203,
      journey: ["/home", "/profile", "/settings", "/notifications"],
      impactedScreens: { anrs: ["/profile"] },
    },
    {
      sessionId: "sess_mock_006",
      startTime: t(7, 22),
      durationMs: 428000,
      user: "user_2840",
      qualityScore: 0.58,
      issues: [
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 6 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 4 },
        { type: "NON_FATAL", label: "Non-Fatals", count: 2 },
      ],
      platform: "Android",
      spanCount: 312,
      journey: ["/home", "/shop", "/product", "/reviews", "/cart"],
      impactedScreens: { nonFatals: ["/product", "/reviews"] },
    },
    {
      sessionId: "sess_mock_007",
      startTime: t(9, 5),
      durationMs: 78000,
      user: "vip_user_01",
      qualityScore: 0.91,
      issues: [
        { type: "NETWORK_ERROR", label: "Network Errors", count: 5 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
      ],
      platform: "Web",
      spanCount: 142,
      journey: ["/dashboard", "/reports", "/export"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_008",
      startTime: t(11, 40),
      durationMs: 12000,
      user: null,
      qualityScore: null,
      issues: [{ type: "CRASH", label: "Crashes", count: 1 }],
      platform: "iOS",
      spanCount: 18,
      journey: ["/onboarding", "/permissions"],
      impactedScreens: { crashes: ["/permissions"] },
    },
    {
      sessionId: "sess_mock_009",
      startTime: t(14, 12),
      durationMs: 602000,
      user: "user_7777",
      qualityScore: 0.82,
      issues: [
        { type: "NON_FATAL", label: "Non-Fatals", count: 4 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 1 },
      ],
      platform: "Android",
      spanCount: 445,
      journey: [
        "/home",
        "/live",
        "/match",
        "/stats",
        "/chat",
        "/wallet",
        "/withdraw",
      ],
      impactedScreens: {
        nonFatals: ["/withdraw", "/wallet"],
      },
    },
    {
      sessionId: "sess_mock_010",
      startTime: t(16, 33),
      durationMs: 195000,
      user: "user_1122",
      qualityScore: 0.44,
      issues: [
        { type: "ANR", label: "ANRs", count: 2 },
        { type: "CRASH", label: "Crashes", count: 1 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 3 },
      ],
      platform: "Android",
      spanCount: 88,
      journey: ["/home", "/video", "/fullscreen"],
      impactedScreens: {
        anrs: ["/fullscreen"],
        crashes: ["/video"],
      },
    },
    {
      sessionId: "sess_mock_011",
      startTime: t(18, 50),
      durationMs: 67000,
      user: "qa_bot_session",
      qualityScore: 0.99,
      issues: [],
      platform: "Web",
      spanCount: 72,
      journey: ["/", "/smoke", "/health"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_012",
      startTime: t(22, 8),
      durationMs: 340000,
      user: "user_9999",
      qualityScore: 0.63,
      issues: [
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 2 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 1 },
      ],
      platform: "iOS",
      spanCount: 198,
      journey: ["/tab/home", "/tab/explore", "/tab/profile"],
      impactedScreens: { nonFatals: ["/tab/explore"] },
    },
  ];
}

function sessionMatchesQuickFilters(
  session: SessionItem,
  quickKeys: string[] | undefined,
): boolean {
  if (!quickKeys?.length) return true;
  const hasCrash = session.issues.some((i) => i.type === "CRASH");
  const hasErrors = session.issues.length > 0;
  return quickKeys.every((key) => {
    if (key === "has_crashes") return hasCrash;
    if (key === "has_errors") return hasErrors;
    return true;
  });
}

function sessionMatchesSearch(session: SessionItem, query: string | undefined) {
  if (!query?.trim()) return true;
  const q = query.trim().toLowerCase();
  return (
    session.sessionId.toLowerCase().includes(q) ||
    (session.user?.toLowerCase().includes(q) ?? false) ||
    session.journey.some((p) => p.toLowerCase().includes(q))
  );
}

function sortMockSessions(
  sessions: SessionItem[],
  sortBy: SessionListingRequest["sortBy"],
  sortDirection: SessionListingRequest["sortDirection"],
): SessionItem[] {
  const dir = sortDirection === "ASC" ? 1 : -1;
  const copy = [...sessions];
  const num = (v: number | null) => (v == null ? -1 : v);

  const getIssueSum = (s: SessionItem, type: string) =>
    s.issues.find((i) => i.type === type)?.count ?? 0;

  copy.sort((a, b) => {
    switch (sortBy) {
      case "DURATION":
        return (a.durationMs - b.durationMs) * dir;
      case "QUALITY_SCORE":
        return (num(a.qualityScore) - num(b.qualityScore)) * dir;
      case "NETWORK_ERRORS":
        return (
          (getIssueSum(a, "NETWORK_ERROR") - getIssueSum(b, "NETWORK_ERROR")) *
          dir
        );
      case "CRASHES":
        return (getIssueSum(a, "CRASH") - getIssueSum(b, "CRASH")) * dir;
      case "ANRS":
        return (getIssueSum(a, "ANR") - getIssueSum(b, "ANR")) * dir;
      case "SLOW_INTERACTIONS":
        return (
          (getIssueSum(a, "SLOW_INTERACTION") -
            getIssueSum(b, "SLOW_INTERACTION")) *
          dir
        );
      case "SPAN_COUNT":
        return (a.spanCount - b.spanCount) * dir;
      case "START_TIME":
      default:
        return (
          (new Date(a.startTime).getTime() - new Date(b.startTime).getTime()) *
          dir
        );
    }
  });
  return copy;
}

/**
 * Returns mock session listing response (same contract as POST /v1/sessions/listing).
 */
export function getMockSessionListingResponse(
  request: SessionListingRequest,
): SessionListingResponse {
  const limit = Math.min(100, Math.max(1, request.page?.limit ?? 10));
  const cursor = request.page?.cursor;

  let allSessions = getMockSessionItems().filter(
    (s) =>
      sessionMatchesQuickFilters(s, request.filters?.quick) &&
      sessionMatchesSearch(s, request.query),
  );
  allSessions = sortMockSessions(
    allSessions,
    request.sortBy ?? "START_TIME",
    request.sortDirection ?? "DESC",
  );

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

export function getMockSessionsFiltersResponse(): FilterConfigResponse {
  const numberOperators = [
    { key: "equals", label: "equals", valueType: "single" as const },
    {
      key: "not_equals",
      label: "does not equal",
      valueType: "single" as const,
    },
    {
      key: "greater_than",
      label: "greater than",
      valueType: "single" as const,
    },
    { key: "less_than", label: "less than", valueType: "single" as const },
    {
      key: "greater_than_or_equal",
      label: "greater than or equal to",
      valueType: "single" as const,
    },
    {
      key: "less_than_or_equal",
      label: "less than or equal to",
      valueType: "single" as const,
    },
  ];
  const stringOperators = [
    { key: "equals", label: "equals", valueType: "single" as const },
    {
      key: "not_equals",
      label: "does not equal",
      valueType: "single" as const,
    },
    { key: "contains", label: "contains", valueType: "single" as const },
    {
      key: "not_contains",
      label: "does not contain",
      valueType: "single" as const,
    },
  ];

  return {
    quick: [
      {
        key: "has_errors",
        displayName: "Has errors",
        description: "Sessions with errors",
      },
      {
        key: "has_crashes",
        displayName: "Has crashes",
        description: "Sessions with crashes",
      },
    ],
    advanced: [
      {
        categoryKey: "session_property",
        displayName: "Session",
        fields: [
          {
            key: "session.duration_ms",
            displayName: "Duration (ms)",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "session.error_count",
            displayName: "Error Count",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "session.page_count",
            displayName: "Page Count",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "session.journey",
            displayName: "Journey Path",
            dataType: "string",
            allowedOperators: stringOperators,
          },
          {
            key: "session.quality_score",
            displayName: "Quality score",
            dataType: "float",
            allowedOperators: numberOperators,
          },
          {
            key: "session.span_count",
            displayName: "Span count",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
        ],
      },
      {
        categoryKey: "device",
        displayName: "Device",
        fields: [
          {
            key: "device.type",
            displayName: "Device Type",
            dataType: "string",
            allowedOperators: [
              { key: "equals", label: "equals", valueType: "single" as const },
              {
                key: "not_equals",
                label: "does not equal",
                valueType: "single" as const,
              },
              { key: "in", label: "is one of", valueType: "array" as const },
              {
                key: "not_in",
                label: "is not one of",
                valueType: "array" as const,
              },
            ],
          },
          {
            key: "device.os_version",
            displayName: "OS Version",
            dataType: "string",
            allowedOperators: stringOperators,
          },
        ],
      },
      {
        categoryKey: "ui_interaction",
        displayName: "UI Interaction",
        fields: [
          {
            key: "critical_interaction.name",
            displayName: "Critical Interaction",
            dataType: "string",
            allowedOperators: [
              { key: "equals", label: "equals", valueType: "single" as const },
              {
                key: "not_equals",
                label: "does not equal",
                valueType: "single" as const,
              },
              {
                key: "contains",
                label: "contains",
                valueType: "single" as const,
              },
              {
                key: "not_contains",
                label: "does not contain",
                valueType: "single" as const,
              },
            ],
          },
        ],
      },
      {
        categoryKey: "user_property",
        displayName: "User",
        fields: [
          {
            key: "user.id",
            displayName: "User ID",
            dataType: "string",
            allowedOperators: stringOperators,
          },
          {
            key: "user.is_anonymous",
            displayName: "Anonymous session",
            dataType: "string",
            allowedOperators: [
              { key: "equals", label: "equals", valueType: "single" as const },
            ],
          },
          {
            key: "user.segment",
            displayName: "User segment",
            dataType: "string",
            allowedOperators: stringOperators,
          },
        ],
      },
      {
        categoryKey: "rum",
        displayName: "RUM metrics",
        fields: [
          {
            key: "rum.lcp_ms",
            displayName: "LCP (ms)",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "rum.fid_ms",
            displayName: "FID (ms)",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "rum.cls",
            displayName: "CLS",
            dataType: "float",
            allowedOperators: numberOperators,
          },
          {
            key: "rum.long_task_count",
            displayName: "Long task count",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
        ],
      },
      {
        categoryKey: "performance",
        displayName: "Performance",
        fields: [
          {
            key: "performance.freeze_frame_count",
            displayName: "Frozen frames",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
          {
            key: "performance.slow_frame_pct",
            displayName: "Slow frame %",
            dataType: "float",
            allowedOperators: numberOperators,
          },
          {
            key: "performance.memory_peak_mb",
            displayName: "Peak memory (MB)",
            dataType: "integer",
            allowedOperators: numberOperators,
          },
        ],
      },
      {
        categoryKey: "event",
        displayName: "Events",
        fields: [
          {
            key: "event.category",
            displayName: "Event category",
            dataType: "string",
            allowedOperators: stringOperators,
          },
          {
            key: "event.name",
            displayName: "Event name",
            dataType: "string",
            allowedOperators: stringOperators,
          },
          {
            key: "event.has_error",
            displayName: "Has client error",
            dataType: "string",
            allowedOperators: [
              { key: "equals", label: "equals", valueType: "single" as const },
            ],
          },
        ],
      },
      {
        categoryKey: "geography",
        displayName: "Geography",
        fields: [
          {
            key: "geography.country",
            displayName: "Country",
            dataType: "string",
            allowedOperators: [
              { key: "equals", label: "equals", valueType: "single" as const },
              { key: "in", label: "is one of", valueType: "array" as const },
              ...stringOperators.filter((o) => o.key !== "equals"),
            ],
          },
          {
            key: "geography.region",
            displayName: "Region",
            dataType: "string",
            allowedOperators: stringOperators,
          },
        ],
      },
      {
        categoryKey: "release",
        displayName: "Release",
        fields: [
          {
            key: "app.version",
            displayName: "App version",
            dataType: "string",
            allowedOperators: stringOperators,
          },
          {
            key: "app.build",
            displayName: "Build number",
            dataType: "string",
            allowedOperators: stringOperators,
          },
        ],
      },
    ],
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

/** Base timestamp for mock snapshot events; aligns with snapshotsDataRange0to4 (first meta frame). */
export const MOCK_SNAPSHOT_BASE_TS = 1774856492514;

/**
 * Mock GET .../snapshots-data?start_blob_key=&end_blob_key=
 * Captured payload for start=0, end=4 (392×807 screenshots). Single-blob "1" kept for tests.
 */
export function getMockSnapshotsData(
  startBlobKey = "0",
  endBlobKey = "4",
): SnapshotsDataResponse["data"] {
  if (startBlobKey === "1" && endBlobKey === "1") {
    return getMockSnapshotsDataBlob1();
  }
  // Large captured API fixture; load lazily so TS/ESLint stay fast.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require("./snapshotsDataRange0to4.json")
    .data as SnapshotsDataResponse["data"];
}

/** Alternate tiny blob for start=1, end=1 (optional tests). */
function getMockSnapshotsDataBlob1(): SnapshotsDataResponse["data"] {
  const blob1Base = MOCK_SNAPSHOT_BASE_TS + 8843;
  return {
    snapshots: [
      {
        timestamp: blob1Base,
        type: 4,
        data: { height: 914, href: "", width: 411 },
      },
      {
        timestamp: blob1Base,
        type: 2,
        data: {
          initialOffset: { left: 0, top: 0 },
          wireframes: [
            {
              height: 914,
              id: 300000001,
              style: {},
              type: "screenshot",
              width: 411,
              x: 0,
              y: 0,
            },
          ],
        },
      },
      {
        timestamp: blob1Base + 1000,
        type: 3,
        data: {
          adds: [],
          removes: [],
          source: 0,
          updates: [
            {
              parentId: null,
              wireframe: {
                base64:
                  "UklGRvw1AABXRUJQVlA4WAoAAAAgAAAANwQAXwkASUNDUMgBAAAAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADY=",
                height: 914,
                id: 300000001,
                style: {},
                type: "screenshot",
                width: 411,
                x: 0,
                y: 0,
              },
            },
          ],
        },
      },
      {
        timestamp: blob1Base + 3000,
        type: 3,
        data: {
          adds: [],
          removes: [],
          source: 0,
          updates: [
            {
              parentId: null,
              wireframe: {
                base64:
                  "UklGRmY/AABXRUJQVlA4WAoAAAAgAAAANwQAXwkASUNDUMgBAAAAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADY=",
                height: 914,
                id: 300000001,
                style: {},
                type: "screenshot",
                width: 411,
                x: 0,
                y: 0,
              },
            },
          ],
        },
      },
      {
        timestamp: blob1Base + 5000,
        type: 3,
        data: {
          adds: [],
          removes: [],
          source: 0,
          updates: [
            {
              parentId: null,
              wireframe: {
                base64:
                  "UklGRg4kAABXRUJQVlA4WAoAAAAgAAAANwQAXwkASUNDUMgBAAAAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADY=",
                height: 914,
                id: 300000001,
                style: {},
                type: "screenshot",
                width: 411,
                x: 0,
                y: 0,
              },
            },
          ],
        },
      },
      {
        timestamp: blob1Base + 7000,
        type: 3,
        data: {
          adds: [],
          removes: [],
          source: 0,
          updates: [
            {
              parentId: null,
              wireframe: {
                base64:
                  "UklGRvQlAABXRUJQVlA4WAoAAAAgAAAANwQAXwkASUNDUMgBAAAAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADY=",
                height: 914,
                id: 300000001,
                style: {},
                type: "screenshot",
                width: 411,
                x: 0,
                y: 0,
              },
            },
          ],
        },
      },
    ],
  };
}
