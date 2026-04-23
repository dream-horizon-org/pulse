export const SEARCH_PLACEHOLDER = "Search by name";

export const FILTER_STATUS_LABEL = "Status";

export const FILTER_CREATED_BY_LABEL = "Created by";

export const FILTER_TAGS_LABEL = "Tags";

export const STATUS_OPTION_ALL = "All statuses";

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
