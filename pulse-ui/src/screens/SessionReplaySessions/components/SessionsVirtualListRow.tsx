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
  formatImpactedScreensPreview,
  formatImpactedScreensTooltip,
  formatImpactedInteractionsCellTooltip,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";

/** Default row height (estimate); virtualizer measures real height per row when content wraps. */
export const ESTIMATED_ROW_HEIGHT = 72;

export const COLUMN_WIDTHS = {
  startTime: "16%",
  duration: "11%",
  user: "11%",
  quality: "10%",
  platform: "10%",
  issues: "12%",
  impactedScreens: "30%",
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
  const pathPreview = formatImpactedScreensPreview(session.impactedScreens);
  const hasPathSummary = pathPreview !== SESSION_LIST_LABELS.noImpactedScreens;

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

      <div
        style={{
          width: COLUMN_WIDTHS.impactedScreens,
          flexShrink: 0,
          overflow: "hidden",
        }}
      >
        {hasInteractionPills ? (
          <Tooltip
            label={formatImpactedInteractionsCellTooltip(
              interactionNames,
              session.impactedScreens,
            )}
            multiline
            maw={320}
          >
            <Group gap={8} style={{ flexWrap: "wrap" }}>
              {interactionNames.map((name, idx) => (
                <Badge
                  key={`${session.sessionId}-${name}-${idx}`}
                  size="sm"
                  variant="light"
                  color="teal"
                  tt="uppercase"
                  fw={700}
                  styles={{ label: { fontWeight: 700 } }}
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
            maw={300}
          >
            <Text
              size="sm"
              c={!hasPathSummary ? "dimmed" : undefined}
              className={classes.journey}
              style={{
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {pathPreview}
            </Text>
          </Tooltip>
        )}
      </div>
    </div>
  );
}
