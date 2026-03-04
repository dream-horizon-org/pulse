import { Box, Text, Stack, ScrollArea, Group } from "@mantine/core";
import { useMemo, useEffect, useRef, useState } from "react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../../screens/SessionTimeline/utils/flameChartTransform";
import type { AttributeValue } from "../../../types/attributes";

interface RawSessionEventsProps {
  sessionData: SessionDetailData;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  onEventClick?: (node: FlameChartNode) => void;
}

type EventType =
  | "session_start"
  | "app_lifecycle"
  | "screen_load"
  | "critical_interaction"
  | "api_call"
  | "interaction_tap"
  | "db_query"
  | "network_performance"
  | "console_log";

interface UnifiedEvent {
  timestamp: number;
  type: EventType;
  description: string;
  color: string;
}

export function RawSessionEvents({
  sessionData,
  scrollToTimestamp,
  onEventClick,
}: RawSessionEventsProps) {
  const scrollViewportRef = useRef<HTMLDivElement>(null);
  const eventRefs = useRef<Map<number, HTMLDivElement>>(new Map());
  const [highlightedTimestamp, setHighlightedTimestamp] = useState<
    number | null
  >(null);

  // Combine all events into a unified timeline
  const unifiedEvents = useMemo<UnifiedEvent[]>(() => {
    const events: UnifiedEvent[] = [];

    // Session start
    events.push({
      timestamp: 0,
      type: "session_start",
      description: "Session Started",
      color: "#6b7280",
    });

    // Add app lifecycle init (if available)
    if (sessionData.events.length > 0) {
      const firstEvent = sessionData.events[0];
      if (firstEvent.timestamp > 0) {
        events.push({
          timestamp: Math.min(850, firstEvent.timestamp - 100),
          type: "app_lifecycle",
          description: "App Lifecycle Init",
          color: "#3b82f6", // Blue
        });
      }
    }

    // Add events
    sessionData.events.forEach((event) => {
      let type: EventType = "session_start";
      let color = "#6b7280";
      let description = event.description;

      if (event.type === "click") {
        type = "interaction_tap";
        color = "#ec4899";
        // Format: "Interaction Tap - Contest"
        const match = event.description.match(
          /(?:Click|Tap|Interaction)\s+(?:on\s+)?(.+)/i,
        );
        description = match
          ? `Interaction Tap - ${match[1]}`
          : event.description;
      } else if (event.type === "navigation") {
        type = "screen_load";
        color = "#0ec9c2";
        // Format: "Screen Load - /HOME"
        const match = event.description.match(
          /(?:Navigate|Navigation|Screen)\s+(?:to\s+)?(.+)/i,
        );
        description = match ? `Screen Load - ${match[1]}` : event.description;
      } else if (event.type === "api_call") {
        type = "api_call";
        color = "#10b981";
        // Format: "API Call - GET /api/search"
        const match = event.description.match(/(?:API|Call)\s+(?:to\s+)?(.+)/i);
        description = match ? `API Call - ${match[1]}` : event.description;
      } else if (event.type === "error") {
        type = "network_performance";
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
        events.push({
          timestamp: interaction.timestamp,
          type: "critical_interaction",
          description: `Critical Interaction - ${interaction.displayName} CII - ${interaction.status === "success" ? "Success" : "Started"}`,
          color: "#8b5cf6", // Purple for critical interactions
        });
      }
    });

    // Add network requests
    sessionData.networkRequests.forEach((req) => {
      events.push({
        timestamp: req.timestamp,
        type: "api_call",
        description: `API Call - ${req.method} ${req.url}`,
        color: req.status >= 200 && req.status < 300 ? "#10b981" : "#ef4444",
      });

      if (req.duration > 2000) {
        events.push({
          timestamp: req.timestamp + req.duration,
          type: "network_performance",
          description: `Network Performance Slow - ${req.duration}ms`,
          color: "#8b5cf6",
        });
      }
    });

    // Sort by timestamp
    return events.sort((a, b) => a.timestamp - b.timestamp);
  }, [sessionData]);

  const getEventTypeLabel = (type: EventType): string => {
    const labels: Record<EventType, string> = {
      session_start: "Session Started",
      app_lifecycle: "App Lifecycle",
      screen_load: "Screen Load",
      critical_interaction: "Critical Interaction",
      api_call: "API Call",
      interaction_tap: "Interaction Tap",
      db_query: "Db Query",
      network_performance: "Network Performance Slow",
      console_log: "Console Log",
    };
    return labels[type] || type;
  };

  // Convert UnifiedEvent to FlameChartNode format
  const convertEventToFlameChartNode = (
    event: UnifiedEvent,
  ): FlameChartNode => {
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

    // Generate IDs based on event
    const id = `raw_event_${event.timestamp}_${event.type}`;
    const traceId = `trace_${event.timestamp}`;
    const spanId = `span_${event.timestamp}`;

    // Find additional context from original data
    let additionalMetadata: Record<string, AttributeValue> = {};
    let duration = 0;

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
  };

  const handleEventClick = (event: UnifiedEvent) => {
    if (onEventClick) {
      const node = convertEventToFlameChartNode(event);
      onEventClick(node);
    }
  };

  // Scroll to timestamp range when scrollToTimestamp changes
  useEffect(() => {
    if (!scrollToTimestamp) return;

    const { t0, t1 } = scrollToTimestamp;

    // First, try to find the critical interaction event itself (exact match at t0)
    let targetEvent = unifiedEvents.find(
      (event) =>
        event.type === "critical_interaction" && event.timestamp === t0,
    );

    // If not found, find the first event within the range [t0, t1]
    if (!targetEvent) {
      targetEvent = unifiedEvents.find(
        (event) => event.timestamp >= t0 && event.timestamp <= t1,
      );
    }

    // If still not found, find the closest event to t0
    if (!targetEvent && unifiedEvents.length > 0) {
      targetEvent = unifiedEvents.reduce((closest, event) => {
        const closestDiff = Math.abs(closest.timestamp - t0);
        const eventDiff = Math.abs(event.timestamp - t0);
        return eventDiff < closestDiff ? event : closest;
      }, unifiedEvents[0]);
    }

    if (!targetEvent) return;

    // Wait for DOM to be ready, then scroll
    const scrollTimeout = setTimeout(() => {
      const eventElement = eventRefs.current.get(targetEvent.timestamp);
      const scrollContainer = scrollViewportRef.current;

      if (!eventElement || !scrollContainer) {
        console.warn("Scroll elements not found", {
          eventElement: !!eventElement,
          scrollContainer: !!scrollContainer,
          targetTimestamp: targetEvent.timestamp,
          availableRefs: Array.from(eventRefs.current.keys()),
        });
        return;
      }

      try {
        // Find element by data attribute as fallback
        const targetElementByAttr = scrollContainer.querySelector(
          `[data-event-timestamp="${targetEvent.timestamp}"]`,
        ) as HTMLElement | null;

        const elementToScroll = targetElementByAttr || eventElement;

        if (!elementToScroll) {
          console.warn("Could not find element to scroll to");
          return;
        }

        // Use getBoundingClientRect for accurate positioning
        const containerRect = scrollContainer.getBoundingClientRect();
        const elementRect = elementToScroll.getBoundingClientRect();
        const currentScrollTop = scrollContainer.scrollTop;

        // Calculate scroll position: element position relative to container + current scroll
        const relativeTop =
          elementRect.top - containerRect.top + currentScrollTop;
        const containerHeight = scrollContainer.clientHeight;
        const elementHeight = elementRect.height;
        const scrollPosition =
          relativeTop - containerHeight / 2 + elementHeight / 2;

        console.log("Scrolling to event", {
          targetTimestamp: targetEvent.timestamp,
          relativeTop,
          scrollPosition,
          currentScrollTop,
          containerHeight,
          elementHeight,
        });

        // Scroll to position - try multiple methods for compatibility
        if (scrollContainer.scrollTo) {
          scrollContainer.scrollTo({
            top: Math.max(0, scrollPosition),
            behavior: "smooth",
          });
        } else {
          scrollContainer.scrollTop = Math.max(0, scrollPosition);
        }

        // Also set directly as backup
        scrollContainer.scrollTop = Math.max(0, scrollPosition);

        // Highlight the event temporarily
        setHighlightedTimestamp(targetEvent.timestamp);
        setTimeout(() => {
          setHighlightedTimestamp(null);
        }, 2000);
      } catch (error) {
        console.error("Error scrolling to event:", error);
        // Fallback: try scrollIntoView with options
        try {
          eventElement.scrollIntoView({
            behavior: "smooth",
            block: "center",
            inline: "nearest",
          });
          setHighlightedTimestamp(targetEvent.timestamp);
          setTimeout(() => {
            setHighlightedTimestamp(null);
          }, 2000);
        } catch (fallbackError) {
          console.error("Fallback scroll also failed:", fallbackError);
        }
      }
    }, 300);

    return () => clearTimeout(scrollTimeout);
  }, [scrollToTimestamp, unifiedEvents]);

  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        Raw Session Events
      </Text>
      <ScrollArea
        h={400}
        viewportRef={scrollViewportRef}
        type="scroll"
        styles={{
          viewport: {
            "& > div": {
              display: "block !important",
            },
          },
        }}
      >
        <Stack gap={0}>
          {unifiedEvents.map((event, idx) => {
            const isHighlighted = highlightedTimestamp === event.timestamp;
            const isInRange = scrollToTimestamp
              ? event.timestamp >= scrollToTimestamp.t0 &&
                event.timestamp <= scrollToTimestamp.t1
              : false;

            return (
              <Box
                key={idx}
                data-event-timestamp={event.timestamp}
                ref={(el) => {
                  if (el) {
                    eventRefs.current.set(event.timestamp, el);
                  }
                }}
                p="xs"
                onClick={() => handleEventClick(event)}
                style={{
                  backgroundColor:
                    isHighlighted || isInRange
                      ? "var(--mantine-color-blue-1)"
                      : idx % 2 === 0
                        ? "var(--mantine-color-gray-0)"
                        : "transparent",
                  borderLeft: `3px solid ${event.color}`,
                  paddingLeft: "12px",
                  transition: "background-color 0.3s ease",
                  cursor: onEventClick ? "pointer" : "default",
                }}
                onMouseEnter={(e) => {
                  if (onEventClick) {
                    e.currentTarget.style.backgroundColor =
                      "var(--mantine-color-gray-1)";
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isHighlighted && !isInRange) {
                    e.currentTarget.style.backgroundColor =
                      idx % 2 === 0
                        ? "var(--mantine-color-gray-0)"
                        : "transparent";
                  }
                }}
              >
                <Group gap="md" wrap="nowrap">
                  <Text
                    size="xs"
                    c="dimmed"
                    ff="monospace"
                    style={{ minWidth: "60px" }}
                  >
                    {event.timestamp}ms
                  </Text>
                  <Box
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: "50%",
                      backgroundColor: event.color,
                      flexShrink: 0,
                    }}
                  />
                  <Text size="sm" style={{ flex: 1 }}>
                    {event.description}
                  </Text>
                </Group>
              </Box>
            );
          })}
        </Stack>
      </ScrollArea>
    </Box>
  );
}
