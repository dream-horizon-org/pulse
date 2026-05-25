import {
  useRef,
  useCallback,
  useEffect,
  type CSSProperties,
  type ReactNode,
} from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Text, Badge, Box, Center, Loader } from "@mantine/core";
import type { ExceptionRow } from "./ExceptionTable.interface";
import { TableSkeleton } from "../../../../components/Skeletons";
import { ExceptionVirtualRow } from "./ExceptionVirtualRow";
import {
  EXCEPTION_LIST_FETCH_MORE_STRIP_HEIGHT_PX,
  EXCEPTION_LIST_HEADER_HEIGHT_PX,
  EXCEPTION_LIST_ROW_HEIGHT_PX,
  getExceptionListColumnWidths,
} from "./exceptionList.constants";
import classes from "../../AppVitals.module.css";

export interface ExceptionVirtualTableProps {
  title: string;
  icon: ReactNode;
  badgeColor: string;
  emptyIcon: string;
  emptyMessage: string;
  exceptions: ExceptionRow[];
  totalCount?: number;
  isLoading: boolean;
  isError: boolean;
  errorMessage?: string;
  onRowClick: (groupId: string) => void;
  onLoadMore: () => void;
  hasMore: boolean;
  isFetchingMore: boolean;
  showTypeColumn?: boolean;
}

const headerCellStyle: CSSProperties = {
  padding: "0 16px",
  display: "flex",
  alignItems: "center",
  fontWeight: 700,
  fontSize: 12,
  color: "var(--mantine-color-dark-6)",
  flexShrink: 0,
};

export function ExceptionVirtualTable({
  title,
  icon,
  badgeColor,
  emptyIcon,
  emptyMessage,
  exceptions,
  totalCount,
  isLoading,
  isError,
  errorMessage,
  onRowClick,
  onLoadMore,
  hasMore,
  isFetchingMore,
  showTypeColumn = false,
}: ExceptionVirtualTableProps) {
  const columnWidths = getExceptionListColumnWidths(showTypeColumn);
  const columnCount = showTypeColumn ? 7 : 6;
  const parentRef = useRef<HTMLDivElement>(null);
  const sentinelObserverRef = useRef<IntersectionObserver | null>(null);
  const onLoadMoreRef = useRef(onLoadMore);
  onLoadMoreRef.current = onLoadMore;

  const hasMoreRef = useRef(hasMore);
  const isFetchingMoreRef = useRef(isFetchingMore);
  const isLoadingRef = useRef(isLoading);
  hasMoreRef.current = hasMore;
  isFetchingMoreRef.current = isFetchingMore;
  isLoadingRef.current = isLoading;

  useEffect(() => {
    return () => {
      sentinelObserverRef.current?.disconnect();
      sentinelObserverRef.current = null;
    };
  }, []);

  const virtualizer = useVirtualizer({
    count: exceptions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => EXCEPTION_LIST_ROW_HEIGHT_PX,
    overscan: 12,
  });

  const virtualItems = virtualizer.getVirtualItems();
  const totalRowHeight = virtualizer.getTotalSize();
  const showFetchMoreStrip = isFetchingMore && hasMore && exceptions.length > 0;
  const fetchMoreStripHeight = showFetchMoreStrip
    ? EXCEPTION_LIST_FETCH_MORE_STRIP_HEIGHT_PX
    : 0;
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
          !isFetchingMoreRef.current &&
          !isLoadingRef.current;

        if (shouldFire) {
          onLoadMoreRef.current();
        }
      },
      {
        root: parentRef.current,
        threshold: 0.1,
        rootMargin: "120px",
      },
    );

    sentinelObserverRef.current = observer;
    observer.observe(el);
  }, []);

  const badgeLabel =
    totalCount != null && totalCount > 0
      ? totalCount.toLocaleString()
      : exceptions.length.toLocaleString();

  if (isLoading) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.issueTableWrapper}>
          <TableSkeleton columns={columnCount} rows={8} />
        </Box>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.issueTableWrapper} style={{ padding: "2rem" }}>
          <Text size="sm" c="red" ta="center">
            {errorMessage || "Failed to load data"}
          </Text>
        </Box>
      </Box>
    );
  }

  if (exceptions.length === 0) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.emptyTableState}>
          <Box className={classes.emptyTableIcon}>{emptyIcon}</Box>
          <Text className={classes.emptyTableText}>{emptyMessage}</Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={`${classes.issueListTable} ${classes.fadeIn}`}>
      <Box className={classes.tableHeader}>
        <Box className={classes.tableHeaderContent}>
          {icon}
          <Text className={classes.tableHeaderTitle}>{title}</Text>
          <Badge size="sm" variant="light" color={badgeColor} ml="auto">
            {badgeLabel}
          </Badge>
        </Box>
      </Box>

      <Box
        className={classes.issueTableWrapper}
        style={{ display: "flex", flexDirection: "column" }}
      >
        {/* Column header */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            height: EXCEPTION_LIST_HEADER_HEIGHT_PX,
            flexShrink: 0,
            borderBottom: "2px solid rgba(14, 201, 194, 0.15)",
            background:
              "linear-gradient(135deg, rgba(14, 201, 194, 0.03), rgba(14, 201, 194, 0.06))",
          }}
        >
          <div style={{ ...headerCellStyle, width: columnWidths.title }}>
            Title
          </div>
          {showTypeColumn && "type" in columnWidths && (
            <div
              style={{
                ...headerCellStyle,
                width: columnWidths.type,
              }}
            >
              Type
            </div>
          )}
          <div style={{ ...headerCellStyle, width: columnWidths.appVersions }}>
            App Versions
          </div>
          <div style={{ ...headerCellStyle, width: columnWidths.occurrences }}>
            Occurrences
          </div>
          <div
            style={{ ...headerCellStyle, width: columnWidths.affectedUsers }}
          >
            Affected Users
          </div>
          <div style={{ ...headerCellStyle, width: columnWidths.firstSeen }}>
            First Seen
          </div>
          <div style={{ ...headerCellStyle, width: columnWidths.lastSeen }}>
            Last Seen
          </div>
        </div>

        <div
          ref={parentRef}
          className={classes.exceptionVirtualScroll}
          data-test="exception-virtual-scroll"
        >
          <div
            style={{
              height: `${scrollContentHeight}px`,
              width: "100%",
              position: "relative",
            }}
          >
            {virtualItems.map((virtualItem) => {
              const exception = exceptions[virtualItem.index];
              return (
                <div
                  key={`${exception.id}-${virtualItem.index}`}
                  style={{
                    position: "absolute",
                    top: 0,
                    left: 0,
                    width: "100%",
                    height: EXCEPTION_LIST_ROW_HEIGHT_PX,
                    transform: `translateY(${virtualItem.start}px)`,
                  }}
                >
                  <ExceptionVirtualRow
                    exception={exception}
                    badgeColor={badgeColor}
                    columnWidths={columnWidths}
                    showTypeColumn={showTypeColumn}
                    onRowClick={onRowClick}
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
                  height: EXCEPTION_LIST_FETCH_MORE_STRIP_HEIGHT_PX,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 10,
                  borderTop: "1px solid #e9ecef",
                  backgroundColor: "#fafafa",
                }}
                data-test="exception-fetch-more-indicator"
              >
                <Loader size="sm" color="red" />
                <Text size="sm" c="dimmed">
                  Loading more...
                </Text>
              </div>
            )}

            <div
              ref={handleSentinelRef}
              style={{
                position: "absolute",
                top: `${totalRowHeight + fetchMoreStripHeight}px`,
                left: 0,
                width: "100%",
                height: "1px",
              }}
              data-test="exception-infinite-scroll-sentinel"
            />
          </div>
        </div>

        {!hasMore && exceptions.length > 0 && !isFetchingMore && (
          <Center py="md" style={{ borderTop: "1px solid #e9ecef" }}>
            <Text size="sm" c="dimmed">
              End of results
            </Text>
          </Center>
        )}
      </Box>
    </Box>
  );
}
