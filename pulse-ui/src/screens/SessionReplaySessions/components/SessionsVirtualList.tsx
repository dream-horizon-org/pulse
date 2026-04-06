import { useEffect, useRef } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import {
  Text,
  Badge,
  Group,
  Tooltip,
  Loader,
  Center,
  Stack,
} from "@mantine/core";
import {
  IconArrowUp,
  IconArrowDown,
  IconArrowsSort,
} from "@tabler/icons-react";
import type { SessionItem } from "../../../services/sessionReplay";
import type { SortField, SortDirection } from "../../../services/sessionReplay";
import {
  SESSION_LIST_LABELS,
  TABLE_COLUMN_LABELS,
} from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  getIssueBadgeColor,
  formatImpactedScreensPreview,
  formatImpactedScreensTooltip,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";

const ESTIMATED_ROW_HEIGHT = 60;
const HEADER_HEIGHT = 56;
const COLUMN_WIDTHS = {
  startTime: "16%",
  duration: "11%",
  user: "11%",
  quality: "10%",
  issues: "12%",
  platform: "10%",
  impactedScreens: "30%",
};

interface SortIconProps {
  column: SortField;
  currentSortBy: SortField;
  sortDirection: SortDirection;
}

function SortIcon({ column, currentSortBy, sortDirection }: SortIconProps) {
  const isActive = currentSortBy === column;
  const Icon = isActive
    ? sortDirection === "ASC"
      ? IconArrowUp
      : IconArrowDown
    : IconArrowsSort;

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: 18,
        marginLeft: 2,
        verticalAlign: "middle",
      }}
    >
      <Icon
        size={14}
        style={{
          opacity: isActive ? 1 : 0.35,
          color: isActive ? "var(--mantine-color-teal-6)" : undefined,
        }}
      />
    </span>
  );
}

interface VirtualRowProps {
  session: SessionItem;
  onSessionClick: (sessionId: string) => void;
}

function VirtualRow({ session, onSessionClick }: VirtualRowProps) {
  const hasIssues = session.issues.length > 0;
  const hasQuality =
    session.qualityScore != null && Number.isFinite(session.qualityScore);

  return (
    <div
      onClick={() => onSessionClick(session.sessionId)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSessionClick(session.sessionId);
        }
      }}
      role="link"
      tabIndex={0}
      style={{
        display: "flex",
        alignItems: "center",
        borderBottom: "1px solid #e9ecef",
        backgroundColor: "white",
        cursor: "pointer",
        transition: "background-color 0.15s ease",
        height: ESTIMATED_ROW_HEIGHT,
        padding: "0 var(--mantine-spacing-md)",
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLElement).style.backgroundColor = "#f8f9fa";
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLElement).style.backgroundColor = "white";
      }}
      className={classes.tableRow}
    >
      <div style={{ width: COLUMN_WIDTHS.startTime, flexShrink: 0 }}>
        <Text size="sm">{formatTimestamp(session.startTime)}</Text>
      </div>

      <div style={{ width: COLUMN_WIDTHS.duration, flexShrink: 0 }}>
        <Text size="sm">{formatDuration(session.durationMs)}</Text>
      </div>

      <div style={{ width: COLUMN_WIDTHS.user, flexShrink: 0 }}>
        <Text size="sm">
          {session.user ?? SESSION_LIST_LABELS.anonymousUser}
        </Text>
      </div>

      <div style={{ width: COLUMN_WIDTHS.quality, flexShrink: 0 }}>
        <Text
          size="sm"
          fw={hasQuality ? 600 : undefined}
          className={!hasQuality ? classes.qualityNa : undefined}
          c={
            hasQuality
              ? getQualityColor(session.qualityScore as number)
              : undefined
          }
        >
          {hasQuality
            ? (session.qualityScore as number).toFixed(2)
            : SESSION_LIST_LABELS.noQuality}
        </Text>
      </div>

      <div style={{ width: COLUMN_WIDTHS.issues, flexShrink: 0 }}>
        {!hasIssues ? (
          <Badge color="teal" variant="light" size="sm">
            {SESSION_LIST_LABELS.clean}
          </Badge>
        ) : (
          <Group gap={4} style={{ flexWrap: "wrap" }}>
            {session.issues.map((issue) => (
              <Badge
                key={issue.type}
                color={getIssueBadgeColor(issue.type)}
                variant={issue.type === "CRASH" ? "filled" : "light"}
                size="sm"
              >
                {issue.count > 1
                  ? `${issue.label} (${issue.count})`
                  : issue.label}
              </Badge>
            ))}
          </Group>
        )}
      </div>

      <div style={{ width: COLUMN_WIDTHS.platform, flexShrink: 0 }}>
        <Badge
          size="sm"
          variant="light"
          color={getPlatformColor(session.platform)}
        >
          {session.platform}
        </Badge>
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.impactedScreens,
          flexShrink: 0,
          overflow: "hidden",
        }}
      >
        <Tooltip
          label={formatImpactedScreensTooltip(session.impactedScreens)}
          multiline
          maw={300}
        >
          <Text
            size="sm"
            c={
              formatImpactedScreensPreview(session.impactedScreens) ===
              SESSION_LIST_LABELS.noImpactedScreens
                ? "dimmed"
                : undefined
            }
            className={classes.journey}
            style={{
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {formatImpactedScreensPreview(session.impactedScreens)}
          </Text>
        </Tooltip>
      </div>
    </div>
  );
}

export interface SessionsVirtualListProps {
  sessions: SessionItem[];
  sortBy: SortField;
  sortDirection: SortDirection;
  onSort: (field: SortField) => void;
  onSessionClick: (sessionId: string) => void;
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
  const tailRowRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: sessions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: 10,
  });

  const virtualItems = virtualizer.getVirtualItems();

  useEffect(() => {
    if (!tailRowRef.current) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !isFetching && !isLoading) {
          onLoadMore();
        }
      },
      {
        root: parentRef.current,
        threshold: 0.1,
        rootMargin: "100px",
      },
    );

    observer.observe(tailRowRef.current);

    return () => observer.disconnect();
  }, [hasMore, isFetching, isLoading, onLoadMore]);

  if (isLoading) {
    return (
      <div className={classes.loadingContainer}>
        <Loader />
        <Text>Loading sessions...</Text>
      </div>
    );
  }

  if (sessions.length === 0 && !isFetching) {
    return (
      <div className={classes.emptyState}>
        <div className={classes.emptyStateIcon}>📋</div>
        <div className={classes.emptyStateTitle}>No sessions found</div>
        <div className={classes.emptyStateDescription}>
          Try adjusting your filters or time range to see more results.
        </div>
      </div>
    );
  }

  return (
    <div className={classes.tableContainer}>
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          borderBottom: "1px solid #e9ecef",
          backgroundColor: "white",
          height: HEADER_HEIGHT,
          padding: "0 var(--mantine-spacing-md)",
          position: "sticky",
          top: 0,
          zIndex: 10,
        }}
      >
        <div
          style={{
            width: COLUMN_WIDTHS.startTime,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
          onClick={() => onSort("START_TIME")}
        >
          <Group gap={4}>
            <Text fw={500} size="sm">
              {TABLE_COLUMN_LABELS.startTime}
            </Text>
            <SortIcon
              column="START_TIME"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Group>
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.duration,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
          onClick={() => onSort("DURATION")}
        >
          <Group gap={4}>
            <Text fw={500} size="sm">
              {TABLE_COLUMN_LABELS.duration}
            </Text>
            <SortIcon
              column="DURATION"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Group>
        </div>

        <div style={{ width: COLUMN_WIDTHS.user, flexShrink: 0 }}>
          <Text fw={500} size="sm">
            {TABLE_COLUMN_LABELS.user}
          </Text>
        </div>

        <div
          style={{
            width: COLUMN_WIDTHS.quality,
            flexShrink: 0,
            cursor: "pointer",
            userSelect: "none",
          }}
          onClick={() => onSort("QUALITY_SCORE")}
        >
          <Group gap={4}>
            <Text fw={500} size="sm">
              {TABLE_COLUMN_LABELS.quality}
            </Text>
            <SortIcon
              column="QUALITY_SCORE"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Group>
        </div>

        <div style={{ width: COLUMN_WIDTHS.issues, flexShrink: 0 }}>
          <Text fw={500} size="sm">
            {TABLE_COLUMN_LABELS.issues}
          </Text>
        </div>

        <div style={{ width: COLUMN_WIDTHS.platform, flexShrink: 0 }}>
          <Text fw={500} size="sm">
            {TABLE_COLUMN_LABELS.platform}
          </Text>
        </div>

        <div style={{ width: COLUMN_WIDTHS.impactedScreens, flexShrink: 0 }}>
          <Text fw={500} size="sm">
            {TABLE_COLUMN_LABELS.impactedScreens}
          </Text>
        </div>
      </div>

      {/* Virtual Rows */}
      <div
        ref={parentRef}
        style={{
          height: "600px",
          overflow: "auto",
        }}
      >
        <div
          style={{
            height: `${virtualizer.getTotalSize()}px`,
            width: "100%",
            position: "relative",
          }}
        >
          {virtualItems.map((virtualItem) => {
            const session = sessions[virtualItem.index];
            const isLastItem = virtualItem.index === sessions.length - 1;

            return (
              <div
                key={`${session.sessionId}-${virtualItem.index}`}
                ref={isLastItem ? tailRowRef : undefined}
                style={{
                  position: "absolute",
                  top: 0,
                  left: 0,
                  width: "100%",
                  transform: `translateY(${virtualItem.start}px)`,
                }}
              >
                <VirtualRow session={session} onSessionClick={onSessionClick} />
              </div>
            );
          })}
        </div>
      </div>

      {/* Loading Footer */}
      {isFetching && (
        <Center p="lg" style={{ borderTop: "1px solid #e9ecef" }}>
          <Stack align="center" gap={8}>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading more...
            </Text>
          </Stack>
        </Center>
      )}

      {/* End of List */}
      {!hasMore && sessions.length > 0 && (
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
    </div>
  );
}
