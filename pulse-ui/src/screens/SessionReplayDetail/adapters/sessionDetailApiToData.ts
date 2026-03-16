/**
 * Adapter: Session Detail API response (contract) → SessionDetailData shape
 * so the existing SessionReplayDetail UI and transformToFlameChart keep working.
 */

import type {
  SessionDetailApiResponse,
  SessionDetailEvent,
  SessionDetailException,
} from "../../../services/sessionReplay/types";
import type {
  SessionDetailData,
  CriticalInteraction,
  SessionEvent,
  NetworkRequest,
} from "../../../services/sessionReplay/mockSessionDetail";

const TRACE_FIELDS = [
  "traceId",
  "spanId",
  "parentSpanId",
  "spanName",
  "timestamp",
  "duration",
  "statusCode",
  "spanType",
  "pulseType",
  "serviceName",
];

const LOG_FIELDS = [
  "traceId",
  "spanId",
  "timestamp",
  "severityText",
  "severityNumber",
  "body",
  "eventName",
  "pulseType",
  "serviceName",
  "scopeName",
  "logAttributesJson",
  "resourceAttributesJson",
];

const EXCEPTION_FIELDS = [
  "timestamp",
  "eventName",
  "title",
  "exceptionMessage",
  "exceptionType",
  "screenName",
  "traceId",
  "spanId",
  "groupId",
  "pulseType",
];

function toSafeISOString(ms: number): string {
  if (typeof ms !== "number" || !Number.isFinite(ms))
    return "1970-01-01T00:00:00.000Z";
  const d = new Date(ms);
  const time = d.getTime();
  if (Number.isNaN(time)) return "1970-01-01T00:00:00.000Z";
  return d.toISOString();
}

/** Parse event timestamp (ISO string or relative ms number) to relative ms from session start. */
function parseEventTimestampMs(timestamp: string, baseMs: number): number {
  if (typeof timestamp !== "string" || !timestamp) return 0;
  const parsed = Date.parse(timestamp);
  if (Number.isFinite(parsed)) return parsed - baseMs;
  const rel = Number(timestamp);
  return Number.isFinite(rel) ? rel : 0;
}

function parseStatusToNumber(status: string): number {
  const n = parseInt(status, 10);
  return Number.isFinite(n) ? n : 0;
}

/** API returns geography as string; UI expects { country, city }. */
function parseGeography(
  geography: string,
): { country: string; city: string } | undefined {
  if (typeof geography !== "string" || !geography.trim()) return undefined;
  return { country: geography.trim(), city: "" };
}

/**
 * Convert API response to SessionDetailData.
 * Contract has no traces/logs tables; we build them from events[] and exceptions[].
 */
export function sessionDetailApiToData(
  api: SessionDetailApiResponse,
): SessionDetailData {
  const startTimeMs =
    typeof api.startTime === "string" && api.startTime
      ? new Date(api.startTime).getTime()
      : Date.now();
  const baseMs = Number.isFinite(startTimeMs) ? startTimeMs : Date.now();

  const traces = buildTracesFromEvents(api.events ?? [], baseMs);
  const logs = buildEmptyLogs();
  const exceptions = buildExceptionsFromApi(api.exceptions ?? [], baseMs);

  const criticalInteractions: CriticalInteraction[] = (
    api.interactions ?? []
  ).map((i, index) => ({
    interactionId: index + 1,
    interactionName: i.interactionName,
    displayName: i.interactionName,
    status: i.status === "success" ? "success" : "failed",
    latency: i.durationMs,
    apdexScore: i.apdexScore,
  }));

  const events: SessionEvent[] = (api.events ?? []).map((e) => ({
    timestamp: parseEventTimestampMs(e.timestamp, baseMs),
    type: e.eventType === "interaction" ? "click" : e.eventType,
    eventType: e.eventType,
    description: e.description,
    durationNs: e.durationNs,
    traceId: e.traceId,
    spanId: e.spanId,
  }));

  const networkRequests: NetworkRequest[] = (api.networkRequests ?? []).map(
    (n) => ({
      timestamp: n.timestamp,
      method: n.method,
      url: n.url,
      status: parseStatusToNumber(n.status),
      duration: n.durationNs / 1e6,
      ...(n.target && { target: n.target }),
    }),
  );

  return {
    sessionId: api.sessionId,
    userId: api.userId,
    isAnonymous: api.isAnonymous,
    startTime: api.startTime,
    duration: api.duration,
    platform: api.platform,
    device: api.device,
    os: api.osVersion,
    appVersion: api.appVersion,
    geography: parseGeography(api.geography),
    interactionQuality:
      typeof api.quality === "number" && Number.isFinite(api.quality)
        ? api.quality
        : 0,
    sessionType: "exploration",
    detectedIssues: [],
    criticalInteractions,
    journey: api.journey ?? [],
    traces,
    logs,
    exceptions,
    events,
    consoleLogs: [],
    networkRequests,
    performance: {
      interactionMetrics: (api.interactions ?? []).map((i, index) => ({
        interactionId: index + 1,
        interactionName: i.interactionName,
        duration: i.durationMs,
        apdexScore: i.apdexScore,
      })),
    },
  };
}

function buildTracesFromEvents(
  events: SessionDetailEvent[],
  startTimeMs: number,
): SessionDetailData["traces"] {
  if (!events.length) {
    return { fields: TRACE_FIELDS, rows: [] };
  }
  const rows: (string | number | null)[][] = events.map((e) => {
    const relTs = parseEventTimestampMs(e.timestamp, startTimeMs);
    const iso = toSafeISOString(startTimeMs + relTs);
    const durationMs =
      typeof e.durationNs === "number" ? e.durationNs / 1e6 : 0;
    return [
      e.traceId,
      e.spanId,
      "",
      e.description || e.eventType,
      iso,
      durationMs,
      "UNSET",
      e.eventType,
      e.eventType,
      "session-replay",
    ];
  });
  return { fields: TRACE_FIELDS, rows };
}

function buildEmptyLogs(): SessionDetailData["logs"] {
  return {
    fields: LOG_FIELDS,
    rows: [],
  };
}

function buildExceptionsFromApi(
  exceptions: SessionDetailException[],
  startTimeMs: number,
): SessionDetailData["exceptions"] {
  if (!exceptions.length) {
    return { fields: EXCEPTION_FIELDS, rows: [] };
  }
  const rows: (string | number | null)[][] = exceptions.map((e) => {
    const relTs =
      typeof e.timestamp === "number" && Number.isFinite(e.timestamp)
        ? e.timestamp
        : 0;
    const iso = toSafeISOString(startTimeMs + relTs);
    return [
      iso,
      "error",
      e.title,
      e.exceptionStackTrace,
      e.title,
      "",
      e.traceId,
      e.spanId,
      e.spanId || "",
      e.pulseType,
    ];
  });
  return { fields: EXCEPTION_FIELDS, rows };
}
