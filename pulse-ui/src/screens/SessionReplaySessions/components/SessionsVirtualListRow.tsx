import { Text, Badge, Group, Tooltip } from "@mantine/core";
import {
  IconArrowUp,
  IconArrowDown,
  IconArrowsSort,
} from "@tabler/icons-react";
import type {
  SessionItem,
  SortField,
  SortDirection,
} from "../../../services/sessionReplay";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  getIssueBadgeColor,
  formatImpactedScreensTooltip,
  formatImpactedInteractionsCellTooltip,
  listImpactedScreensLines,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";

/** Default row height (estimate); virtualizer measures real height per row when content wraps. */
export const ESTIMATED_ROW_HEIGHT = 72;

export const COLUMN_WIDTHS = {
  startTime: "16%",
  duration: "11%",
  user: "13%",
  quality: "10%",
  issues: "14%",
  platform: "10%",
  impactedScreens: "26%",
} as const;

interface SortIconProps {
  column: SortField;
  currentSortBy: SortField;
  sortDirection: SortDirection;
}

export function SortIcon({
  column,
  currentSortBy,
  sortDirection,
}: SortIconProps) {
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

export function VirtualRow({ session, onSessionClick }: VirtualRowProps) {
  const hasIssues = session.issues.length > 0;
  const hasQuality =
    session.qualityScore != null && Number.isFinite(session.qualityScore);
  const interactionNames =
    session.impactedInteractionNames?.filter(Boolean) ?? [];
  const hasInteractionPills = interactionNames.length > 0;
  const pathLines = listImpactedScreensLines(session.impactedScreens);
  const hasPathChips = pathLines.length > 0;

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
        boxSizing: "border-box",
        borderBottom: "1px solid #e9ecef",
        backgroundColor: "white",
        cursor: "pointer",
        transition: "background-color 0.15s ease",
        minHeight: ESTIMATED_ROW_HEIGHT,
        padding: "10px var(--mantine-spacing-md)",
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

      <div
        style={{ width: COLUMN_WIDTHS.user, flexShrink: 0, overflow: "hidden" }}
      >
        <Tooltip
          label={session.user ?? SESSION_LIST_LABELS.anonymousUser}
          withArrow
          openDelay={300}
          disabled={!session.user}
        >
          <Text
            size="sm"
            style={{
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {session.user ?? SESSION_LIST_LABELS.anonymousUser}
          </Text>
        </Tooltip>
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.quality,
          flexShrink: 0,
          paddingLeft: "0.5rem",
        }}
      >
        <Text
          size="sm"
          fw={hasQuality ? 600 : undefined}
          c={
            hasQuality
              ? getQualityColor(session.qualityScore as number)
              : "dimmed"
          }
        >
          {hasQuality
            ? (session.qualityScore as number).toFixed(2)
            : SESSION_LIST_LABELS.noQuality}
        </Text>
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

      <div style={{ width: COLUMN_WIDTHS.issues, flexShrink: 0 }}>
        {!hasIssues ? (
          <Badge color="teal" variant="light" size="sm">
            {SESSION_LIST_LABELS.clean}
          </Badge>
        ) : (
          <Group gap={8} style={{ flexWrap: "wrap" }}>
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

      <div style={{ width: COLUMN_WIDTHS.impactedScreens, flexShrink: 0 }}>
        {!hasInteractionPills && !hasPathChips ? (
          <Text size="sm" c="dimmed">
            {SESSION_LIST_LABELS.noImpactedScreens}
          </Text>
        ) : hasInteractionPills ? (
          <Tooltip
            label={formatImpactedInteractionsCellTooltip(
              interactionNames,
              session.impactedScreens,
            )}
            multiline
            maw={320}
          >
            <Group gap={8} style={{ flexWrap: "wrap" }}>
              {interactionNames.map((name, index) => (
                <Badge
                  key={`${name}-${index}`}
                  size="sm"
                  variant="light"
                  color="teal"
                  styles={{
                    label: { textTransform: "uppercase", fontWeight: 600 },
                  }}
                >
                  {name}
                </Badge>
              ))}
            </Group>
          </Tooltip>
        ) : (
          <Tooltip
            label={formatImpactedScreensTooltip(session.impactedScreens)}
            multiline
            maw={320}
          >
            <Group gap={8} style={{ flexWrap: "wrap" }}>
              {pathLines.map((line, index) => (
                <Badge
                  key={`${line}-${index}`}
                  size="sm"
                  variant="light"
                  color="teal"
                >
                  {line}
                </Badge>
              ))}
            </Group>
          </Tooltip>
        )}
      </div>
    </div>
  );
}
