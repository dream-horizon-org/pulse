import { Box, Text, Stack, Badge, ActionIcon, Tooltip } from "@mantine/core";
import { useRef, useMemo, useEffect } from "react";
import { useParams } from "react-router-dom";
import { IconExternalLink } from "@tabler/icons-react";
import type { UnifiedEvent } from "./utils/unifiedEvents";
import type { FlameChartNode } from "../../../SessionTimeline/utils/flameChartTransform";
import { convertEventToFlameChartNode } from "./utils/eventConverter";
import type { SessionDetailData } from "../../../../services/sessionReplay/mockSessionDetail";
import { ROUTES } from "../../../../constants";
import { HEADERS } from "../../constants/strings";
import classes from "./EventList.module.css";

function formatAbsoluteTime(sessionStartIso: string, offsetMs: number): string {
  const date = new Date(new Date(sessionStartIso).getTime() + offsetMs);
  const month = date.toLocaleString("en-US", { month: "short" });
  const day = date.getDate();
  const h = date.getHours().toString().padStart(2, "0");
  const m = date.getMinutes().toString().padStart(2, "0");
  const s = date.getSeconds().toString().padStart(2, "0");
  const year = date.getFullYear();
  return `${month} ${day} ${year}, ${h}:${m}:${s}`;
}

interface EventListProps {
  unifiedEvents: UnifiedEvent[];
  sessionData: SessionDetailData;
  currentTime?: number;
  /** When false, timeline does not auto-scroll on seek/skip (avoids page jump). */
  isPlaying?: boolean;
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
  isPlaying = false,
  scrollToTimestamp,
  highlightedTimestamp,
  eventRefs,
  scrollViewportRef,
  onEventClick,
}: EventListProps) {
  const { projectId } = useParams<{ projectId: string }>();
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

  // Follow playback in the timeline only while playing (skip/seek while paused must not scrollIntoView).
  useEffect(() => {
    if (!isPlaying) return;
    if (
      playbackHighlightTimestamp == null ||
      playbackHighlightTimestamp === lastPlaybackScrollRef.current
    )
      return;
    lastPlaybackScrollRef.current = playbackHighlightTimestamp;

    const eventElement = eventRefs.current.get(playbackHighlightTimestamp);
    if (!eventElement) return;

    const rafId = requestAnimationFrame(() => {
      const targetEl = scrollViewportRef.current?.querySelector(
        `[data-event-timestamp="${playbackHighlightTimestamp}"]`,
      ) as HTMLElement | null;
      const el = targetEl || eventElement;
      el.scrollIntoView({ block: "nearest", behavior: "smooth" });
    });
    return () => cancelAnimationFrame(rafId);
  }, [isPlaying, playbackHighlightTimestamp, eventRefs, scrollViewportRef]);

  const handleEventClick = (event: UnifiedEvent) => {
    if (onEventClick) {
      const node = convertEventToFlameChartNode(event, sessionData);
      onEventClick(node);
    }
  };

  return (
    <Box style={{ display: "flex", flexDirection: "column" }}>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.RAW_SESSION_EVENTS}
      </Text>
      <Box ref={scrollViewportRef} style={{ width: "100%" }}>
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

              const full = event.description.startsWith(
                event.categoryLabel + ": ",
              )
                ? event.description.slice(event.categoryLabel.length + 2)
                : event.description;
              const lastColon = full.lastIndexOf(": ");
              const rawContent =
                lastColon >= 0 ? full.slice(0, lastColon) : full;
              const status = lastColon >= 0 ? full.slice(lastColon + 2) : "";
              let content = rawContent;
              try {
                content = decodeURIComponent(rawContent);
              } catch (error) {
                console.error("Error decoding content", error);
              }
              const path =
                projectId && event.interactionName
                  ? ROUTES.PROJECT_INTERACTION_DETAILS.basePath.replace(
                      ":projectId",
                      projectId,
                    ) +
                    "/" +
                    event.interactionName.replace(/\s+/g, "")
                  : null;

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
                  <Box className={classes.eventRow}>
                    <Text
                      size="xs"
                      c="dimmed"
                      className={classes.eventTime}
                    >
                      {formatAbsoluteTime(
                        sessionData.startTime,
                        event.timestamp,
                      )}
                    </Text>
                    <Box className={classes.eventBadgeWrap}>
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
                    <Box className={classes.eventDetailCell}>
                      <Text size="sm" className={classes.eventDetailText}>
                        {content}
                      </Text>
                      {event.interactionName && projectId && path ? (
                        <Tooltip label="Open interaction details" withArrow>
                          <ActionIcon
                            variant="subtle"
                            color="teal"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              window.open(path, "_blank");
                            }}
                          >
                            <IconExternalLink size={14} />
                          </ActionIcon>
                        </Tooltip>
                      ) : null}
                    </Box>
                    {status ? (
                      <Text size="sm" c="dimmed" className={classes.eventStatus}>
                        {status}
                      </Text>
                    ) : (
                      <span
                        className={classes.eventStatusPlaceholder}
                        aria-hidden
                      />
                    )}
                  </Box>
                </Box>
              );
            })}
        </Stack>
      </Box>
    </Box>
  );
}
