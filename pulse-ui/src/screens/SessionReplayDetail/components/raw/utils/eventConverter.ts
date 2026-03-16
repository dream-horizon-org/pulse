import type { SessionDetailData } from "../../../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../../../SessionTimeline/utils/flameChartTransform";
import type { AttributeValue } from "../../../../../types/attributes";
import type { UnifiedEvent, EventType } from "./unifiedEvents";

export function convertEventToFlameChartNode(
  event: UnifiedEvent,
  sessionData: SessionDetailData,
): FlameChartNode {
  // Map event types to FlameChartNode types
  const typeMap: Record<
    EventType,
    "span" | "log" | "exception" | "orphan-span" | "orphan-log"
  > = {
    session_start: "log",
    app_lifecycle: "log",
    screen_load: "span",
    critical_interaction: "span",
    api_call: "span",
    interaction_tap: "span",
    db_query: "span",
    network_performance: "log",
    console_log: "log",
  };

  const id = `raw_event_${event.timestamp}_${event.type}`;
  const traceId = event.traceId ?? `${event.timestamp}`;
  const spanId = event.spanId ?? `${event.timestamp}`;

  let additionalMetadata: Record<string, AttributeValue> = {};
  let duration = event.durationMs ?? 0;

  // Try to find matching network request
  const matchingNetworkRequest = sessionData.networkRequests.find(
    (req) =>
      req.timestamp === event.timestamp ||
      (event.type === "api_call" && event.description.includes(req.url)),
  );

  if (matchingNetworkRequest) {
    duration = matchingNetworkRequest.duration;
    additionalMetadata = {
      method: matchingNetworkRequest.method,
      url: matchingNetworkRequest.url,
      status: matchingNetworkRequest.status,
      duration: matchingNetworkRequest.duration,
    };
  }

  // Try to find matching critical interaction
  const matchingInteraction = sessionData.criticalInteractions.find(
    (interaction) => interaction.timestamp === event.timestamp,
  );

  if (matchingInteraction) {
    duration = matchingInteraction.latency || duration;
    const interactionMetadata: Record<string, AttributeValue> = {
      interactionId: matchingInteraction.interactionId,
      interactionName: matchingInteraction.interactionName,
      displayName: matchingInteraction.displayName,
      status: matchingInteraction.status,
    };

    if (matchingInteraction.latency !== undefined) {
      interactionMetadata.latency = matchingInteraction.latency;
    }
    if (matchingInteraction.apdexScore !== undefined) {
      interactionMetadata.apdexScore = matchingInteraction.apdexScore;
    }

    additionalMetadata = {
      ...additionalMetadata,
      ...interactionMetadata,
    };
  }

  // Try to find matching console log
  const matchingLog = sessionData.consoleLogs.find(
    (log) => log.timestamp === event.timestamp,
  );

  if (matchingLog) {
    const logMetadata: Record<string, AttributeValue> = {
      level: matchingLog.level,
      message: matchingLog.message,
    };

    if (matchingLog.stackTrace !== undefined) {
      logMetadata.stackTrace = matchingLog.stackTrace;
    }

    additionalMetadata = {
      ...additionalMetadata,
      ...logMetadata,
    };
  }

  // Create metadata with event details
  const metadata: Record<string, AttributeValue> = {
    timestamp: new Date(sessionData.startTime).getTime() + event.timestamp,
    description: event.description,
    eventType: event.type,
    color: event.color,
    serviceName: "mobile-app",
    pulseType: `raw.${event.type}`,
    ...additionalMetadata,
  };

  return {
    id,
    name: event.description,
    start: event.timestamp,
    duration,
    type: typeMap[event.type] || "log",
    color: event.color,
    traceId,
    spanId,
    parentSpanId: undefined,
    children: [],
    metadata,
  };
}
