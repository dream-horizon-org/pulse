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

function normalizeIsoTimestampForParse(iso: string): string {
  const trimmed = iso.trim();
  const m = trimmed.match(
    /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d+)?(Z|[+-]\d{2}:?\d{2})$/i,
  );
  if (!m) return trimmed;
  const [, dateTime, frac, zone] = m;
  if (!frac || frac.length <= 4) return trimmed;
  return `${dateTime}${frac.slice(0, 4)}${zone}`;
}

/** Parse event timestamp (ISO string or relative ms number) to relative ms from session start. */
function parseEventTimestampMs(
  timestamp: string | number | undefined,
  baseMs: number,
): number {
  if (timestamp == null) return 0;
  if (typeof timestamp === "number") {
    if (!Number.isFinite(timestamp)) return 0;
    return timestamp >= 1e12 ? timestamp - baseMs : timestamp;
  }
  if (typeof timestamp !== "string" || !timestamp.trim()) return 0;
  const normalized = normalizeIsoTimestampForParse(timestamp.trim());
  const parsed = Date.parse(normalized);
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
      ? new Date(normalizeIsoTimestampForParse(api.startTime)).getTime()
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
      timestamp: parseEventTimestampMs(n.timestamp, baseMs),
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
      typeof api.quality === "number" &&
      Number.isFinite(api.quality) &&
      api.quality > 0
        ? api.quality
        : null,
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
    const rawTs = e.timestamp;
    const absMs =
      typeof rawTs === "string"
        ? new Date(normalizeIsoTimestampForParse(rawTs)).getTime()
        : typeof rawTs === "number" && Number.isFinite(rawTs)
          ? rawTs >= 1e12
            ? rawTs
            : startTimeMs + rawTs
          : 0;
    const iso = toSafeISOString(absMs);
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

/**
 * Returns minimal SessionDetailData when the API returns no data and mock is disabled.
 * Use this so the detail page still renders (empty timeline, no journey) instead of mock data.
 */
export function getEmptySessionDetail(sessionId: string): SessionDetailData {
  const baseMs = Date.now();
  return {
    sessionId,
    userId: "",
    isAnonymous: true,
    startTime: new Date(baseMs).toISOString(),
    duration: 0,
    platform: "iOS",
    device: "",
    os: "",
    appVersion: undefined,
    geography: undefined,
    interactionQuality: null,
    sessionType: "exploration",
    detectedIssues: [],
    criticalInteractions: [],
    journey: [],
    traces: buildTracesFromEvents([], baseMs),
    logs: buildEmptyLogs(),
    exceptions: buildExceptionsFromApi([], baseMs),
    events: [],
    consoleLogs: [],
    networkRequests: [],
    performance: { interactionMetrics: [] },
  };
}
