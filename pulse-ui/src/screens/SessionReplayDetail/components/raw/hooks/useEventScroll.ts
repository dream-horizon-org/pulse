import { useEffect, useRef, useState } from "react";
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
  }, [scrollToTimestamp, unifiedEvents, eventRefs, scrollViewportRef]);

  return highlightedTimestamp;
}
