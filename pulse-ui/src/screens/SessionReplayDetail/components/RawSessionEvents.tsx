import { useMemo, useRef } from "react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../../screens/SessionTimeline/utils/flameChartTransform";
import { createUnifiedEvents } from "./raw/utils/unifiedEvents";
import { useEventScroll } from "./raw/hooks/useEventScroll";
import { EventList } from "./raw/EventList";

interface RawSessionEventsProps {
  sessionData: SessionDetailData;
  currentTime?: number;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  onEventClick?: (node: FlameChartNode) => void;
}

export function RawSessionEvents({
  sessionData,
  currentTime = 0,
  scrollToTimestamp,
  onEventClick,
}: RawSessionEventsProps) {
  const scrollViewportRef = useRef<HTMLDivElement>(null);
  const eventRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  // Combine all events into a unified timeline
  const unifiedEvents = useMemo(() => {
    return createUnifiedEvents(sessionData);
  }, [sessionData]);

  // Handle scrolling to timestamp (e.g. critical interaction click)
  const highlightedTimestamp = useEventScroll({
    unifiedEvents,
    scrollToTimestamp,
    eventRefs,
    scrollViewportRef,
  });

  return (
    <EventList
      unifiedEvents={unifiedEvents}
      sessionData={sessionData}
      currentTime={currentTime}
      scrollToTimestamp={scrollToTimestamp}
      highlightedTimestamp={highlightedTimestamp}
      eventRefs={eventRefs}
      scrollViewportRef={scrollViewportRef}
      onEventClick={onEventClick}
    />
  );
}
