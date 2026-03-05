import type { SessionDetailData } from "../../../../../services/sessionReplay/mockSessionDetail";
import {
  EVENT_TYPES,
  EVENT_DESCRIPTIONS,
  STATUS_LABELS,
} from "../../../constants/strings";

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
}

export function createUnifiedEvents(
  sessionData: SessionDetailData,
): UnifiedEvent[] {
  const events: UnifiedEvent[] = [];

  // Session start
  events.push({
    timestamp: 0,
    type: EVENT_TYPES.SESSION_START,
    description: EVENT_DESCRIPTIONS.SESSION_STARTED,
    color: "#6b7280",
  });

  // Add app lifecycle init (if available)
  if (sessionData.events.length > 0) {
    const firstEvent = sessionData.events[0];
    if (firstEvent.timestamp > 0) {
      events.push({
        timestamp: Math.min(850, firstEvent.timestamp - 100),
        type: EVENT_TYPES.APP_LIFECYCLE,
        description: EVENT_DESCRIPTIONS.APP_LIFECYCLE_INIT,
        color: "#3b82f6", // Blue
      });
    }
  }

  // Add events
  sessionData.events.forEach((event) => {
    let type: EventType = EVENT_TYPES.SESSION_START;
    let color = "#6b7280";
    let description = event.description;

    if (event.type === "click") {
      type = EVENT_TYPES.INTERACTION_TAP;
      color = "#ec4899";
      // Format: "Interaction Tap - Contest"
      const match = event.description.match(
        /(?:Click|Tap|Interaction)\s+(?:on\s+)?(.+)/i,
      );
      description = match
        ? `${EVENT_DESCRIPTIONS.INTERACTION_TAP_PREFIX} ${match[1]}`
        : event.description;
    } else if (event.type === "navigation") {
      type = EVENT_TYPES.SCREEN_LOAD;
      color = "#0ec9c2";
      // Format: "Screen Load - /HOME"
      const match = event.description.match(
        /(?:Navigate|Navigation|Screen)\s+(?:to\s+)?(.+)/i,
      );
      description = match
        ? `${EVENT_DESCRIPTIONS.SCREEN_LOAD_PREFIX} ${match[1]}`
        : event.description;
    } else if (event.type === "api_call") {
      type = EVENT_TYPES.API_CALL;
      color = "#10b981";
      // Format: "API Call - GET /api/search"
      const match = event.description.match(/(?:API|Call)\s+(?:to\s+)?(.+)/i);
      description = match
        ? `${EVENT_DESCRIPTIONS.API_CALL_PREFIX} ${match[1]}`
        : event.description;
    } else if (event.type === "error") {
      type = EVENT_TYPES.NETWORK_PERFORMANCE;
      color = "#ef4444";
    }

    events.push({
      timestamp: event.timestamp,
      type,
      description,
      color,
    });
  });

  // Add critical interactions
  sessionData.criticalInteractions.forEach((interaction) => {
    if (interaction.timestamp !== undefined) {
      const statusText =
        interaction.status === "success"
          ? STATUS_LABELS.SUCCESS
          : EVENT_DESCRIPTIONS.CRITICAL_INTERACTION_SUFFIX_STARTED;
      events.push({
        timestamp: interaction.timestamp,
        type: EVENT_TYPES.CRITICAL_INTERACTION,
        description: `${EVENT_DESCRIPTIONS.CRITICAL_INTERACTION_PREFIX} ${interaction.displayName} CII - ${statusText}`,
        color: "#8b5cf6", // Purple for critical interactions
      });
    }
  });

  // Add network requests
  sessionData.networkRequests.forEach((req) => {
    events.push({
      timestamp: req.timestamp,
      type: EVENT_TYPES.API_CALL,
      description: `${EVENT_DESCRIPTIONS.API_CALL_PREFIX} ${req.method} ${req.url}`,
      color: req.status >= 200 && req.status < 300 ? "#10b981" : "#ef4444",
    });

    if (req.duration > 2000) {
      events.push({
        timestamp: req.timestamp + req.duration,
        type: EVENT_TYPES.NETWORK_PERFORMANCE,
        description: `${EVENT_DESCRIPTIONS.NETWORK_PERFORMANCE_SLOW}${req.duration}ms`,
        color: "#8b5cf6",
      });
    }
  });

  // Sort by timestamp
  return events.sort((a, b) => a.timestamp - b.timestamp);
}
