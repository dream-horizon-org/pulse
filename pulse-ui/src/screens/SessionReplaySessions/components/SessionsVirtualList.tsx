import { useRef, useCallback, useEffect } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Text, Loader, Center, Stack } from "@mantine/core";
import type {
  SessionItem,
  SortField,
  SortDirection,
} from "../../../services/sessionReplay";
import {
  SESSION_LIST_SORT_FIELD,
  SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
  TABLE_COLUMN_LABELS,
} from "../constants/sessionList.constants";
import {
  COLUMN_WIDTHS,
  ESTIMATED_ROW_HEIGHT,
  SortIcon,
  VirtualRow,
} from "./SessionsVirtualListRow";

const HEADER_HEIGHT = 56;
/** In-scroll strip above the infinite-scroll sentinel while fetching the next page */
const FETCH_MORE_STRIP_HEIGHT = 52;

export interface SessionsVirtualListProps {
  sessions: SessionItem[];
  sortBy: SortField;
  sortDirection: SortDirection;
  onSort: (field: SortField) => void;
  onSessionClick: (session: SessionItem) => void;
  onLoadMore: () => void;
  isLoading: boolean;
  isFetching: boolean;
  hasMore: boolean;
  error?: Error | null;
}

export function SessionsVirtualList({
  sessions,
  sortBy,
  sortDirection,
  onSort,
  onSessionClick,
  onLoadMore,
  isLoading,
  isFetching,
  hasMore,
  error,
}: SessionsVirtualListProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const sentinelObserverRef = useRef<IntersectionObserver | null>(null);
  const onLoadMoreRef = useRef(onLoadMore);
  onLoadMoreRef.current = onLoadMore;

  const hasMoreRef = useRef(hasMore);
  const isFetchingRef = useRef(isFetching);
  const isLoadingRef = useRef(isLoading);
  hasMoreRef.current = hasMore;
  isFetchingRef.current = isFetching;
  isLoadingRef.current = isLoading;

  useEffect(() => {
    return () => {
      sentinelObserverRef.current?.disconnect();
      sentinelObserverRef.current = null;
    };
  }, []);

  const virtualizer = useVirtualizer({
    count: sessions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: 10,
  });

  const virtualItems = virtualizer.getVirtualItems();
  const totalRowHeight = virtualizer.getTotalSize();
  const showFetchMoreStrip = isFetching && hasMore && sessions.length > 0;
  const fetchMoreStripHeight = showFetchMoreStrip ? FETCH_MORE_STRIP_HEIGHT : 0;
  /** Rows + optional fetch-more strip + 1px sentinel */
  const scrollContentHeight = totalRowHeight + fetchMoreStripHeight + 1;

  const handleSentinelRef = useCallback((el: HTMLDivElement | null) => {
    sentinelObserverRef.current?.disconnect();
    sentinelObserverRef.current = null;

    if (!el) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const shouldFire =
          entries[0]?.isIntersecting &&
          hasMoreRef.current &&
          !isFetchingRef.current &&
          !isLoadingRef.current;

        if (shouldFire) {
          onLoadMoreRef.current();
        }
      },
      {
        root: parentRef.current,
        threshold: 0.1,
        rootMargin: "100px",
      },
    );

    sentinelObserverRef.current = observer;
    observer.observe(el);
  }, []);

  return (
    <div>
      {/* Header Row */}
      <div
        style={{
          display: "flex",
          borderBottom: "2px solid #e9ecef",
          backgroundColor: "#f8f9fa",
          paddingTop: "var(--mantine-spacing-md)",
          paddingBottom: "var(--mantine-spacing-md)",
          paddingLeft: "var(--mantine-spacing-md)",
          paddingRight: "var(--mantine-spacing-md)",
          height: HEADER_HEIGHT,
          alignItems: "center",
          fontWeight: 600,
          fontSize: "var(--mantine-font-size-sm)",
          color: "#495057",
        }}
      >
        <div
          onClick={() => onSort(SESSION_LIST_SORT_FIELD.START_TIME)}
          role="button"
          tabIndex={0}
          style={{
            width: COLUMN_WIDTHS.startTime,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
        >
          {TABLE_COLUMN_LABELS.startTime}
          {sortBy === SESSION_LIST_SORT_FIELD.START_TIME && (
            <SortIcon
              column={SESSION_LIST_SORT_FIELD.START_TIME}
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          )}
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.duration,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
          onClick={() => onSort(SESSION_LIST_SORT_FIELD.DURATION)}
          role="button"
          tabIndex={0}
        >
          {TABLE_COLUMN_LABELS.duration}
          {sortBy === SESSION_LIST_SORT_FIELD.DURATION && (
            <SortIcon
              column={SESSION_LIST_SORT_FIELD.DURATION}
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          )}
        </div>

        <div style={{ width: COLUMN_WIDTHS.user, flexShrink: 0 }}>
          {TABLE_COLUMN_LABELS.user}
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.quality,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
          onClick={() => onSort(SESSION_LIST_SORT_FIELD.QUALITY_SCORE)}
          role="button"
          tabIndex={0}
        >
          {TABLE_COLUMN_LABELS.quality}
          {sortBy === SESSION_LIST_SORT_FIELD.QUALITY_SCORE && (
            <SortIcon
              column={SESSION_LIST_SORT_FIELD.QUALITY_SCORE}
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          )}
        </div>

        <div style={{ width: COLUMN_WIDTHS.platform, flexShrink: 0 }}>
          {TABLE_COLUMN_LABELS.platform}
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.issues,
            flexShrink: 0,
            paddingRight: SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
            boxSizing: "border-box",
          }}
        >
          {TABLE_COLUMN_LABELS.issues}
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.impactedScreens,
            flexShrink: 0,
            paddingLeft: SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
            boxSizing: "border-box",
          }}
        >
          {TABLE_COLUMN_LABELS.impactedScreens}
        </div>
      </div>

      {isLoading && (
        <Center p="lg">
          <Stack align="center" gap={8}>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading sessions...
            </Text>
          </Stack>
        </Center>
      )}

      {!isLoading && sessions.length === 0 && (
        <Center p="lg">
          <Text size="sm" c="dimmed">
            No sessions found
          </Text>
        </Center>
      )}

      {sessions.length > 0 && (
        <>
          {/* Virtual Rows */}
          <div
            ref={parentRef}
            style={{
              height: "calc(100vh - 400px)",
              overflow: "auto",
              minHeight: "600px",
              position: "relative",
            }}
          >
            <div
              style={{
                height: `${scrollContentHeight}px`,
                width: "100%",
                position: "relative",
              }}
            >
              {virtualItems.map((virtualItem) => {
                const session = sessions[virtualItem.index];

                return (
                  <div
                    key={`${session.sessionId}-${virtualItem.index}`}
                    style={{
                      position: "absolute",
                      top: 0,
                      left: 0,
                      width: "100%",
                      height: ESTIMATED_ROW_HEIGHT,
                      transform: `translateY(${virtualItem.start}px)`,
                    }}
                  >
                    <VirtualRow
                      session={session}
                      onSessionClick={onSessionClick}
                    />
                  </div>
                );
              })}

              {showFetchMoreStrip && (
                <div
                  style={{
                    position: "absolute",
                    top: `${totalRowHeight}px`,
                    left: 0,
                    width: "100%",
                    height: FETCH_MORE_STRIP_HEIGHT,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 10,
                    borderTop: "1px solid #e9ecef",
                    backgroundColor: "#fafafa",
                  }}
                  data-test="sessions-fetch-more-indicator"
                >
                  <Loader size="sm" color="teal" />
                  <Text size="sm" c="dimmed">
                    Loading more...
                  </Text>
                </div>
              )}

              {/* Sentinel sits after rows (+ optional fetch-more strip) */}
              <div
                ref={handleSentinelRef}
                style={{
                  position: "absolute",
                  top: `${totalRowHeight + fetchMoreStripHeight}px`,
                  left: 0,
                  width: "100%",
                  height: "1px",
                }}
                data-test="infinite-scroll-sentinel"
              />
            </div>
          </div>

          {/* End of List */}
          {!hasMore && sessions.length > 0 && !isFetching && (
            <Center p="lg" style={{ borderTop: "1px solid #e9ecef" }}>
              <Text size="sm" c="dimmed">
                End of results
              </Text>
            </Center>
          )}

          {/* Error Display */}
          {error && (
            <div
              style={{
                padding: "var(--mantine-spacing-md)",
                borderTop: "1px solid #e9ecef",
                backgroundColor: "#ffe0e0",
                color: "#c92a2a",
              }}
            >
              <Text size="sm" fw={500}>
                Error loading sessions: {error.message}
              </Text>
            </div>
          )}
        </>
      )}
    </div>
  );
}
