import { Text, Badge, Tooltip } from "@mantine/core";
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
import {
  SESSION_LIST_LABELS,
  SESSION_LIST_ROW_HEIGHT_PX,
  SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
} from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  getIssueBadgeColor,
  formatImpactedScreensTooltip,
  formatImpactedInteractionsNamesTooltip,
  listImpactedScreensLines,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";
import { FitMeasuredChipRow } from "./FitMeasuredChipRow";

/** Row height for virtualizer (fixed; no per-row measurement). */
export const ESTIMATED_ROW_HEIGHT = SESSION_LIST_ROW_HEIGHT_PX;

export const COLUMN_WIDTHS = {
  startTime: "15%",
  duration: "11%",
  user: "12%",
  quality: "9%",
  issues: "15%",
  platform: "10%",
  impactedScreens: "28%",
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

function issueBadgeLabel(issue: SessionItem["issues"][number]): string {
  return issue.count > 1 ? `${issue.label} (${issue.count})` : issue.label;
}

/** Label styles so long chip text ellipsizes inside the badge instead of being cut in half */
const truncatingBadgeStyles = {
  root: { maxWidth: "100%" as const },
  label: {
    overflow: "hidden" as const,
    textOverflow: "ellipsis" as const,
    whiteSpace: "nowrap" as const,
  },
};

export function VirtualRow({ session, onSessionClick }: VirtualRowProps) {
  const hasIssues = session.issues.length > 0;
  const hasQuality =
    session.qualityScore != null && Number.isFinite(session.qualityScore);
  const interactionNames =
    session.impactedInteractionNames?.filter(Boolean) ?? [];
  const hasInteractionPills = interactionNames.length > 0;
  const pathLines = listImpactedScreensLines(session.impactedScreens);
  const hasPathChips = pathLines.length > 0;

  const issuesTooltip = session.issues.map(issueBadgeLabel).join(", ");
  const impactedTooltip =
    formatImpactedInteractionsNamesTooltip(interactionNames);
  const pathTooltip = formatImpactedScreensTooltip(session.impactedScreens);

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
        height: SESSION_LIST_ROW_HEIGHT_PX,
        minHeight: SESSION_LIST_ROW_HEIGHT_PX,
        maxHeight: SESSION_LIST_ROW_HEIGHT_PX,
        overflow: "hidden",
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
      <div
        style={{
          width: COLUMN_WIDTHS.startTime,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
        }}
      >
        <Text
          size="sm"
          style={{
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {formatTimestamp(session.startTime)}
        </Text>
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.duration,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
        }}
      >
        <Text
          size="sm"
          style={{
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {formatDuration(session.durationMs)}
        </Text>
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.user,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
        }}
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
          minWidth: 0,
          paddingLeft: "0.5rem",
          overflow: "hidden",
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
          style={{
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {hasQuality
            ? (session.qualityScore as number).toFixed(2)
            : SESSION_LIST_LABELS.noQuality}
        </Text>
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.platform,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
        }}
      >
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
          width: COLUMN_WIDTHS.issues,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
          paddingRight: SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
          boxSizing: "border-box",
        }}
      >
        {!hasIssues ? (
          <Badge color="teal" variant="light" size="sm">
            {SESSION_LIST_LABELS.clean}
          </Badge>
        ) : (
          <Tooltip label={issuesTooltip} multiline maw={300}>
            <div style={{ width: "100%", minWidth: 0 }}>
              <FitMeasuredChipRow
                items={session.issues}
                getKey={(issue, index) => `${issue.type}-${index}`}
                renderChip={(issue, _index, { lone }) => (
                  <Badge
                    color={getIssueBadgeColor(issue.type)}
                    variant={issue.type === "CRASH" ? "filled" : "light"}
                    size="sm"
                    style={lone ? { width: "100%" } : undefined}
                    styles={truncatingBadgeStyles}
                  >
                    {issueBadgeLabel(issue)}
                  </Badge>
                )}
              />
            </div>
          </Tooltip>
        )}
      </div>

      <div
        style={{
          width: COLUMN_WIDTHS.impactedScreens,
          flexShrink: 0,
          minWidth: 0,
          overflow: "hidden",
          paddingLeft: SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX,
          boxSizing: "border-box",
        }}
      >
        {!hasInteractionPills && !hasPathChips ? (
          <Text size="sm" c="dimmed">
            {SESSION_LIST_LABELS.noImpactedScreens}
          </Text>
        ) : hasInteractionPills ? (
          <Tooltip label={impactedTooltip} multiline maw={320}>
            <div style={{ width: "100%", minWidth: 0 }}>
              <FitMeasuredChipRow
                items={interactionNames}
                getKey={(name, index) => `${name}-${index}`}
                renderChip={(name, _index, { lone }) => (
                  <Badge
                    size="sm"
                    variant="light"
                    color="teal"
                    style={lone ? { width: "100%" } : undefined}
                    styles={{
                      root: truncatingBadgeStyles.root,
                      label: {
                        ...truncatingBadgeStyles.label,
                        textTransform: "uppercase",
                        fontWeight: 600,
                      },
                    }}
                  >
                    {name}
                  </Badge>
                )}
              />
            </div>
          </Tooltip>
        ) : (
          <Tooltip label={pathTooltip} multiline maw={320}>
            <div style={{ width: "100%", minWidth: 0 }}>
              <FitMeasuredChipRow
                items={pathLines}
                getKey={(line, index) => `${line}-${index}`}
                renderChip={(line, _index, { lone }) => (
                  <Badge
                    size="sm"
                    variant="light"
                    color="teal"
                    style={lone ? { width: "100%" } : undefined}
                    styles={truncatingBadgeStyles}
                  >
                    {line}
                  </Badge>
                )}
              />
            </div>
          </Tooltip>
        )}
      </div>
    </div>
  );
}
