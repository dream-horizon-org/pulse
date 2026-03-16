import { Box, Text, Stack, ScrollArea, Group, Badge } from "@mantine/core";
import { useRef, useMemo, useEffect } from "react";
import type { UnifiedEvent } from "./utils/unifiedEvents";
import type { FlameChartNode } from "../../../SessionTimeline/utils/flameChartTransform";
import { convertEventToFlameChartNode } from "./utils/eventConverter";
import type { SessionDetailData } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS } from "../../constants/strings";

interface EventListProps {
  unifiedEvents: UnifiedEvent[];
  sessionData: SessionDetailData;
  currentTime?: number;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  highlightedTimestamp: number | null;
  eventRefs: React.MutableRefObject<Map<number, HTMLDivElement>>;
  scrollViewportRef: React.RefObject<HTMLDivElement>;
  onEventClick?: (node: FlameChartNode) => void;
}

export function EventList({
  unifiedEvents,
  sessionData,
  currentTime = 0,
  scrollToTimestamp,
  highlightedTimestamp,
  eventRefs,
  scrollViewportRef,
  onEventClick,
}: EventListProps) {
  const lastPlaybackScrollRef = useRef<number>(-1);

  // Event at current playback time: last event with timestamp <= currentTime
  const playbackHighlightTimestamp = useMemo(() => {
    if (unifiedEvents.length === 0) return null;
    const pastOrCurrent = unifiedEvents.filter(
      (e) => e.timestamp <= currentTime,
    );
    if (pastOrCurrent.length === 0) return unifiedEvents[0].timestamp;
    return pastOrCurrent[pastOrCurrent.length - 1].timestamp;
  }, [unifiedEvents, currentTime]);

  // Scroll the playback-highlighted event into view when it changes
  useEffect(() => {
    if (
      playbackHighlightTimestamp == null ||
      playbackHighlightTimestamp === lastPlaybackScrollRef.current
    )
      return;
    lastPlaybackScrollRef.current = playbackHighlightTimestamp;

    const eventElement = eventRefs.current.get(playbackHighlightTimestamp);
    const scrollContainer = scrollViewportRef.current;
    if (!eventElement || !scrollContainer) return;

    const rafId = requestAnimationFrame(() => {
      const targetEl = scrollContainer.querySelector(
        `[data-event-timestamp="${playbackHighlightTimestamp}"]`,
      ) as HTMLElement | null;
      const el = targetEl || eventElement;
      const containerRect = scrollContainer.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      const currentScrollTop = scrollContainer.scrollTop;
      const relativeTop = elRect.top - containerRect.top + currentScrollTop;
      const scrollPosition =
        relativeTop - containerRect.height / 2 + elRect.height / 2;
      scrollContainer.scrollTo({
        top: Math.max(0, scrollPosition),
        behavior: "smooth",
      });
    });
    return () => cancelAnimationFrame(rafId);
  }, [playbackHighlightTimestamp, eventRefs, scrollViewportRef]);

  const handleEventClick = (event: UnifiedEvent) => {
    if (onEventClick) {
      const node = convertEventToFlameChartNode(event, sessionData);
      onEventClick(node);
    }
  };

  return (
    <Box
      style={{
        display: "flex",
        flexDirection: "column",
        flex: 1,
        minHeight: 0,
      }}
    >
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.RAW_SESSION_EVENTS}
      </Text>
      <Box
        style={{
          flex: 1,
          minHeight: 0,
          display: "flex",
          flexDirection: "column",
        }}
      >
        <ScrollArea
          style={{ flex: 1, minHeight: 0 }}
          viewportRef={scrollViewportRef}
          type="scroll"
          styles={{
            root: {
              flex: 1,
              minHeight: 0,
              display: "flex",
              flexDirection: "column",
            },
            viewport: {
              flex: 1,
              "& > div": {
                display: "block !important",
              },
            },
          }}
        >
          <Stack gap={0}>
            {unifiedEvents.map((event, idx) => {
              const isHighlighted = highlightedTimestamp === event.timestamp;
              const isPlaybackHighlight =
                playbackHighlightTimestamp === event.timestamp;
              const isInRange = scrollToTimestamp
                ? event.timestamp >= scrollToTimestamp.t0 &&
                  event.timestamp <= scrollToTimestamp.t1
                : false;
              const isActive =
                isHighlighted || isPlaybackHighlight || isInRange;

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
                    backgroundColor: isActive
                      ? "var(--mantine-color-teal-1)"
                      : idx % 2 === 0
                        ? "var(--mantine-color-gray-0)"
                        : "transparent",
                    borderLeft: `3px solid ${event.color}`,
                    paddingLeft: "12px",
                    transition: "background-color 0.2s ease",
                    cursor: onEventClick ? "pointer" : "default",
                  }}
                  onMouseEnter={(e) => {
                    if (onEventClick && !isActive) {
                      e.currentTarget.style.backgroundColor =
                        "var(--mantine-color-gray-1)";
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isActive) {
                      e.currentTarget.style.backgroundColor =
                        idx % 2 === 0
                          ? "var(--mantine-color-gray-0)"
                          : "transparent";
                    }
                  }}
                >
                  <Group
                    gap="sm"
                    wrap="nowrap"
                    align="center"
                    style={{ width: "100%" }}
                  >
                    <Text
                      size="xs"
                      c="dimmed"
                      ff="monospace"
                      style={{ minWidth: "56px", flexShrink: 0 }}
                    >
                      {event.timestamp}ms
                    </Text>
                    <Box style={{ minWidth: 100, flexShrink: 0 }}>
                      <Badge
                        size="sm"
                        variant="light"
                        style={{
                          backgroundColor: `${event.color}20`,
                          color: event.color,
                          borderColor: `${event.color}40`,
                          fontWeight: 600,
                        }}
                      >
                        {event.categoryLabel}
                      </Badge>
                    </Box>
                    {(() => {
                      const full = event.description.startsWith(
                        event.categoryLabel + ": ",
                      )
                        ? event.description.slice(
                            event.categoryLabel.length + 2,
                          )
                        : event.description;
                      const lastColon = full.lastIndexOf(": ");
                      const content =
                        lastColon >= 0 ? full.slice(0, lastColon) : full;
                      const status =
                        lastColon >= 0 ? full.slice(lastColon + 2) : "";
                      return (
                        <>
                          <Text size="sm" style={{ flex: 1, minWidth: 0 }}>
                            {content}
                          </Text>
                          {status ? (
                            <Text
                              size="sm"
                              c="dimmed"
                              style={{
                                flexShrink: 0,
                                marginLeft: "var(--mantine-spacing-sm)",
                              }}
                            >
                              {status}
                            </Text>
                          ) : null}
                        </>
                      );
                    })()}
                  </Group>
                </Box>
              );
            })}
          </Stack>
        </ScrollArea>
      </Box>
    </Box>
  );
}
