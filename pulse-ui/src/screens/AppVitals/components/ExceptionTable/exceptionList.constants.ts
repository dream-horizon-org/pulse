/** Rows fetched per infinite-scroll page */
export const EXCEPTION_LIST_PAGE_SIZE = 20;

/** Fixed row height for @tanstack/react-virtual */
export const EXCEPTION_LIST_ROW_HEIGHT_PX = 52;

export const EXCEPTION_LIST_HEADER_HEIGHT_PX = 48;

/** In-scroll strip above the infinite-scroll sentinel while fetching the next page */
export const EXCEPTION_LIST_FETCH_MORE_STRIP_HEIGHT_PX = 48;

export const EXCEPTION_LIST_COLUMN_WIDTHS = {
  title: "28%",
  appVersions: "16%",
  occurrences: "12%",
  affectedUsers: "12%",
  firstSeen: "16%",
  lastSeen: "16%",
} as const;

/** Non-fatal list includes a Type column */
export const EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE = {
  title: "22%",
  type: "12%",
  appVersions: "14%",
  occurrences: "11%",
  affectedUsers: "11%",
  firstSeen: "15%",
  lastSeen: "15%",
} as const;

export type ExceptionListColumnWidths =
  | typeof EXCEPTION_LIST_COLUMN_WIDTHS
  | typeof EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE;

export function getExceptionListColumnWidths(showTypeColumn?: boolean) {
  return showTypeColumn
    ? EXCEPTION_LIST_COLUMN_WIDTHS_WITH_TYPE
    : EXCEPTION_LIST_COLUMN_WIDTHS;
}
