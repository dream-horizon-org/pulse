/** Rows fetched per infinite-scroll page */
export const EXCEPTION_LIST_PAGE_SIZE = 20;

export const EXCEPTION_LIST_SEARCH_DEBOUNCE_MS = 500;

export const EXCEPTION_LIST_SEARCH_PLACEHOLDER =
  "Search by title or app version...";

/** Fixed row height for @tanstack/react-virtual */
export const EXCEPTION_LIST_ROW_HEIGHT_PX = 60;

export const EXCEPTION_LIST_HEADER_HEIGHT_PX = 48;

/** In-scroll strip above the infinite-scroll sentinel while fetching the next page */
export const EXCEPTION_LIST_FETCH_MORE_STRIP_HEIGHT_PX = 48;

export const EXCEPTION_LIST_COLUMN_WIDTHS = {
  title: "38%",
  appVersions: "16%",
  occurrences: "10%",
  affectedUsers: "10%",
  firstSeen: "13%",
  lastSeen: "13%",
} as const;

/** Non-fatal list includes a Type column */
export const EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE = {
  title: "28%",
  type: "12%",
  appVersions: "14%",
  occurrences: "11%",
  affectedUsers: "11%",
  firstSeen: "12%",
  lastSeen: "12%",
} as const;

export type ExceptionListColumnWidths =
  | typeof EXCEPTION_LIST_COLUMN_WIDTHS
  | typeof EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE;

export function getExceptionListColumnWidths(showTypeColumn?: boolean) {
  return showTypeColumn
    ? EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE
    : EXCEPTION_LIST_COLUMN_WIDTHS;
}
