import type { SessionDetailData } from "../../../../../services/sessionReplay/mockSessionDetail";
import {
  EVENT_TYPES,
  EVENT_DESCRIPTIONS,
  STATUS_LABELS,
  RAW_EVENT_CATEGORIES,
} from "../../../constants/strings";
import { sanitizeUrl, sanitizeDisplayText, sanitizePath } from "./sanitize";

const CAT = RAW_EVENT_CATEGORIES;
const NA = "—";

export type EventType =
  | "session_start"
  | "app_lifecycle"
  | "screen_load"
  | "critical_interaction"
  | "api_call"
  | "interaction_tap"
  | "db_query"
  | "network_performance"
  | "console_log";

export interface UnifiedEvent {
  timestamp: number;
  type: EventType;
  description: string;
  color: string;
  /** Display label for chip (e.g. Interaction, Network, Event) */
  categoryLabel: string;
  traceId?: string;
  spanId?: string;
  durationMs?: number;
}

export function createUnifiedEvents(
  sessionData: SessionDetailData,
): UnifiedEvent[] {
  const events: UnifiedEvent[] = [];

  // Session start — Session: Content: —
  events.push({
    timestamp: 0,
    type: EVENT_TYPES.SESSION_START,
    description: `${CAT.SESSION.label}: ${EVENT_DESCRIPTIONS.SESSION_STARTED}: ${NA}`,
    color: CAT.SESSION.color,
    categoryLabel: CAT.SESSION.label,
  });

  // App lifecycle init — Event: Content: —
  if (sessionData.events.length > 0) {
    const firstEvent = sessionData.events[0];
    if (firstEvent.timestamp > 0) {
      events.push({
        timestamp: Math.min(850, firstEvent.timestamp - 100),
        type: EVENT_TYPES.APP_LIFECYCLE,
        description: `${CAT.EVENT.label}: ${EVENT_DESCRIPTIONS.APP_LIFECYCLE_INIT}: ${NA}`,
        color: CAT.EVENT.color,
        categoryLabel: CAT.EVENT.label,
      });
    }
  }

  // Events from session (click, navigation, api_call, error) — Type: Content: Status
  type Category = (typeof CAT)[keyof typeof CAT];
  sessionData.events.forEach((event) => {
    let type: EventType = EVENT_TYPES.SESSION_START;
    let category: Category = CAT.SESSION;
    const descRaw = event.description ?? "";
    let content = sanitizeDisplayText(descRaw);
    let status = NA;

    if (event.type === "click") {
      type = EVENT_TYPES.INTERACTION_TAP;
      category = CAT.INTERACTION;
      const match = descRaw.match(
        /(?:Click|Tap|Interaction)\s+(?:on\s+)?(.+)/i,
      );
      content = sanitizeDisplayText(match ? match[1].trim() : descRaw);
    } else if (event.type === "navigation") {
      type = EVENT_TYPES.SCREEN_LOAD;
      category = CAT.EVENT;
      const match = descRaw.match(
        /(?:Navigate|Navigation|Screen)\s+(?:to\s+)?(.+)/i,
      );
      const rawContent = match ? match[1].trim() : descRaw;
      content = sanitizePath(rawContent);
    } else if (event.type === "api_call") {
      type = EVENT_TYPES.API_CALL;
      category = CAT.NETWORK;
      const match = descRaw.match(/(?:API|Call)\s+(?:to\s+)?(.+)/i);
      const rawContent = match ? match[1].trim() : descRaw;
      content =
        rawContent.includes("/") || /^https?:\/\//i.test(rawContent)
          ? sanitizeUrl(rawContent)
          : sanitizeDisplayText(rawContent);
    } else if (event.type === "error") {
      type = EVENT_TYPES.NETWORK_PERFORMANCE;
      category = CAT.ERROR;
      content = sanitizeDisplayText(descRaw);
      status = "Error";
    }

    events.push({
      timestamp: event.timestamp,
      type,
      description: `${category.label}: ${content}: ${status}`,
      color: category.color,
      categoryLabel: category.label,
      traceId: event.traceId,
      spanId: event.spanId,
      durationMs: event.durationNs
        ? Math.round(event.durationNs / 1_000_000)
        : undefined,
    });
  });

  // Critical interactions — Interaction: Name CII: SUCCESS | FAILED
  sessionData.criticalInteractions.forEach((interaction) => {
    if (interaction.timestamp !== undefined) {
      const statusText =
        interaction.status === "success"
          ? STATUS_LABELS.SUCCESS
          : STATUS_LABELS.FAILED;
      const safeName = sanitizeDisplayText(interaction.displayName);
      events.push({
        timestamp: interaction.timestamp,
        type: EVENT_TYPES.CRITICAL_INTERACTION,
        description: `${CAT.INTERACTION.label}: ${safeName} CII: ${statusText}`,
        color: CAT.INTERACTION.color,
        categoryLabel: CAT.INTERACTION.label,
      });
    }
  });

  // Network requests — Network: METHOD url: STATUS (URL sanitized)
  sessionData.networkRequests.forEach((req) => {
    const statusStr = String(req.status);
    const category =
      req.status >= 200 && req.status < 300 ? CAT.NETWORK : CAT.ERROR;
    const safeUrl = sanitizeUrl(req.url);
    events.push({
      timestamp: req.timestamp,
      type: EVENT_TYPES.API_CALL,
      description: `${category.label}: ${req.method} ${safeUrl}: ${statusStr}`,
      color: category.color,
      categoryLabel: category.label,
    });

    if (req.duration > 2000) {
      events.push({
        timestamp: req.timestamp + req.duration,
        type: EVENT_TYPES.NETWORK_PERFORMANCE,
        description: `${CAT.PERFORMANCE.label}: Slow request ${req.duration}ms: ${NA}`,
        color: CAT.PERFORMANCE.color,
        categoryLabel: CAT.PERFORMANCE.label,
      });
    }
  });

  // Sort by timestamp
  return events.sort((a, b) => a.timestamp - b.timestamp);
}
