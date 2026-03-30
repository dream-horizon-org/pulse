export const FUNNELS_JOURNEYS_PAGE_TITLE = "Funnels & Journeys";

export const FUNNELS_JOURNEYS_SUBTITLE =
  "Browse saved funnels and journeys, or create a new analysis.";

/** Loading line — aligned with Session List copy tone */
export const FUNNELS_JOURNEYS_LOADING = "Loading funnels and journeys…";

export const TAB_FUNNELS = "Funnels";

export const TAB_JOURNEYS = "Journeys";

export const EMPTY_TAB_FUNNEL_TITLE = "No funnels yet";

export const EMPTY_TAB_FUNNEL_DESCRIPTION =
  "Create a funnel to measure step-by-step conversion across events.";

export const EMPTY_TAB_JOURNEY_TITLE = "No journeys yet";

export const EMPTY_TAB_JOURNEY_DESCRIPTION =
  "Create a journey to explore how users move between screens and events.";

export const EMPTY_TAB_FUNNEL_FILTERED_TITLE = "No matching funnels";

export const EMPTY_TAB_JOURNEY_FILTERED_TITLE = "No matching journeys";

export const SEARCH_PLACEHOLDER = "Search by name";

export const CREATE_MENU_LABEL = "Create";

export const CREATE_FUNNEL_ITEM = "New funnel";

export const CREATE_JOURNEY_ITEM = "New journey";

export const EMPTY_FILTERED_DESCRIPTION =
  "Try adjusting search or filters, or create a new funnel or journey.";

export const FILTER_STATUS_LABEL = "Status";

export const FILTER_CREATED_BY_LABEL = "Created by";

export const FILTER_TAGS_LABEL = "Tags";

export const FILTER_TYPE_LABEL = "Type";

export const STATUS_OPTION_ALL = "All statuses";

export const TYPE_OPTION_ALL = "All types";

export const TYPE_OPTION_ORDERED = "Ordered";

export const TYPE_OPTION_UNORDERED = "Unordered";

/** Default rows per page — matches listing API default */
export const DEFAULT_PAGE_SIZE = 10;

export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;

export const PAGINATION_PREVIOUS = "Previous";

export const PAGINATION_NEXT = "Next";

export const PAGINATION_PAGE_LABEL = "Page";

export const PAGINATION_ROWS_PER_PAGE = "Rows per page";

export const PAGINATION_SHOWING_RANGE = (
  from: number,
  to: number,
  total: number,
) => `Showing ${from}–${to} of ${total}`;
