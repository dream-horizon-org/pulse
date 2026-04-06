import { useRef, useCallback } from "react";
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

  const virtualizer = useVirtualizer({
    count: sessions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: 10,
  });

  const virtualItems = virtualizer.getVirtualItems();

  const handleSentinelRef = useCallback(
    (el: HTMLDivElement | null) => {
      if (!el || !hasMore) {
        return;
      }

      const observer = new IntersectionObserver(
        (entries) => {
          if (
            entries[0]?.isIntersecting &&
            hasMore &&
            !isFetching &&
            !isLoading
          ) {
            onLoadMore();
          }
        },
        {
          root: parentRef.current,
          threshold: 0.1,
          rootMargin: "100px",
        },
      );

      observer.observe(el);

      return () => {
        observer.disconnect();
      };
    },
    [hasMore, isFetching, isLoading, onLoadMore, parentRef],
  );

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
          onClick={() => onSort("START_TIME")}
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
          {sortBy === "START_TIME" && (
            <SortIcon
              column="START_TIME"
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
          onClick={() => onSort("DURATION")}
          role="button"
          tabIndex={0}
        >
          {TABLE_COLUMN_LABELS.duration}
          {sortBy === "DURATION" && (
            <SortIcon
              column="DURATION"
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
          onClick={() => onSort("QUALITY_SCORE")}
          role="button"
          tabIndex={0}
        >
          {TABLE_COLUMN_LABELS.quality}
          {sortBy === "QUALITY_SCORE" && (
            <SortIcon
              column="QUALITY_SCORE"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          )}
        </div>

        <div style={{ width: COLUMN_WIDTHS.issues, flexShrink: 0 }}>
          {TABLE_COLUMN_LABELS.issues}
        </div>

        <div style={{ width: COLUMN_WIDTHS.platform, flexShrink: 0 }}>
          {TABLE_COLUMN_LABELS.platform}
        </div>

        <div style={{ width: COLUMN_WIDTHS.impactedScreens, flexShrink: 0 }}>
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
                height: `${virtualizer.getTotalSize()}px`,
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

              {/* Sentinel element for infinite scroll */}
              <div
                ref={handleSentinelRef}
                style={{
                  position: "absolute",
                  top: `${virtualizer.getTotalSize()}px`,
                  left: 0,
                  width: "100%",
                  height: "1px",
                }}
                data-test="infinite-scroll-sentinel"
              />
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
        </>
      )}
    </div>
  );
}
