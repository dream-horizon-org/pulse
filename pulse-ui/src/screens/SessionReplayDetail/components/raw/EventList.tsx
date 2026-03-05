import { Box, Text, Stack, ScrollArea, Group } from "@mantine/core";
import { useRef } from "react";
import type { UnifiedEvent } from "./utils/unifiedEvents";
import type { FlameChartNode } from "../../../SessionTimeline/utils/flameChartTransform";
import { convertEventToFlameChartNode } from "./utils/eventConverter";
import type { SessionDetailData } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS } from "../../constants/strings";

interface EventListProps {
  unifiedEvents: UnifiedEvent[];
  sessionData: SessionDetailData;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  highlightedTimestamp: number | null;
  eventRefs: React.MutableRefObject<Map<number, HTMLDivElement>>;
  scrollViewportRef: React.RefObject<HTMLDivElement>;
  onEventClick?: (node: FlameChartNode) => void;
}

export function EventList({
  unifiedEvents,
  sessionData,
  scrollToTimestamp,
  highlightedTimestamp,
  eventRefs,
  scrollViewportRef,
  onEventClick,
}: EventListProps) {
  const handleEventClick = (event: UnifiedEvent) => {
    if (onEventClick) {
      const node = convertEventToFlameChartNode(event, sessionData);
      onEventClick(node);
    }
  };

  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.RAW_SESSION_EVENTS}
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
