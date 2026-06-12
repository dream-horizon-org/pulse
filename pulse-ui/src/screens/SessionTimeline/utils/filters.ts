import { SessionTimelineEvent } from "../SessionTimeline.interface";
import { OtelEventType } from "../constants/otelConstants";

export const filterSpans = (
  spans: SessionTimelineEvent[],
  activeFilters: Set<OtelEventType>,
  searchQuery: string,
): SessionTimelineEvent[] => {
  let filtered = spans;

  if (activeFilters.size > 0) {
    filtered = filtered.filter((span) => {
      if (activeFilters.has(span.type)) return true;
      if (span.children) {
        return span.children.some((child) => activeFilters.has(child.type));
      }
      return false;
    });
  }

  if (searchQuery) {
    const query = searchQuery.toLowerCase();
    filtered = filtered.filter((span) => {
      const matchesName = span.name.toLowerCase().includes(query);
      const matchesChildren =
        span.children?.some((child) =>
          child.name.toLowerCase().includes(query),
        ) || false;
      return matchesName || matchesChildren;
    });
  }

  return filtered;
};
