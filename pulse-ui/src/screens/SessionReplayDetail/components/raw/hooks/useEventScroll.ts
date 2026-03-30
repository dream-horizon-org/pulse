import { useEffect, useState } from "react";
import type { UnifiedEvent } from "../utils/unifiedEvents";

interface UseEventScrollProps {
  unifiedEvents: UnifiedEvent[];
  scrollToTimestamp?: { t0: number; t1: number } | null;
  eventRefs: React.MutableRefObject<Map<number, HTMLDivElement>>;
  scrollViewportRef: React.RefObject<HTMLDivElement>;
}

export function useEventScroll({
  unifiedEvents,
  scrollToTimestamp,
  eventRefs,
  scrollViewportRef,
}: UseEventScrollProps) {
  const [highlightedTimestamp, setHighlightedTimestamp] = useState<
    number | null
  >(null);

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

      if (!eventElement) {
        return;
      }

      try {
        const targetElementByAttr = scrollContainer?.querySelector(
          `[data-event-timestamp="${targetEvent.timestamp}"]`,
        ) as HTMLElement | null;

        const elementToScroll = targetElementByAttr || eventElement;

        if (!elementToScroll) {
          return;
        }

        elementToScroll.scrollIntoView({
          behavior: "smooth",
          block: "center",
          inline: "nearest",
        });

        setHighlightedTimestamp(targetEvent.timestamp);
        setTimeout(() => {
          setHighlightedTimestamp(null);
        }, 2000);
      } catch (error) {
        console.error("Error scrolling to event:", error);
      }
    }, 300);

    return () => clearTimeout(scrollTimeout);
  }, [scrollToTimestamp, unifiedEvents, eventRefs, scrollViewportRef]);

  return highlightedTimestamp;
}
