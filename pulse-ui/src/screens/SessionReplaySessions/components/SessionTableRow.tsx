import { Text, Badge, Group, Tooltip } from "@mantine/core";
import type { SessionItem } from "../../../services/sessionReplay";
import {
  SESSION_LIST_LABELS,
  ISSUES_DISPLAY_LIMIT,
  IMPACTED_SCREENS_DISPLAY_LIMIT,
} from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  getIssueBadgeColor,
  sortIssuesBySeverity,
  getCriticalInteractionChips,
  getCriticalInteractionsTooltipLines,
  legacyRankToBadgeColor,
  type CriticalInteractionChip,
} from "../utils/sessionListUtils";
import sessionClasses from "../SessionReplaySessions.module.css";
import gridClasses from "./SessionsTable.module.css";

export interface SessionTableRowProps {
  session: SessionItem;
  onSessionClick: (sessionId: string) => void;
}

const badgeRootStyle = { fontFamily: "inherit" as const };

function CriticalInteractionBadges({ chips }: { chips: CriticalInteractionChip[] }) {
  return (
    <Group
      gap={6}
      justify="flex-start"
      align="flex-start"
      wrap="wrap"
      className={gridClasses.criticalInteractionsGroup}
    >
      {chips.map((chip, index) => {
        if (chip.kind === "pulse") {
          return (
            <Badge
              key={`pulse-${chip.name}-${index}`}
              size="sm"
              variant="light"
              color="teal"
              className={gridClasses.criticalInteractionBadge}
              styles={{ root: badgeRootStyle }}
            >
              {chip.name}
            </Badge>
          );
        }
        return (
          <Badge
            key={`legacy-${chip.line}-${index}`}
            size="sm"
            variant="light"
            color={legacyRankToBadgeColor(chip.rank)}
            className={gridClasses.criticalInteractionBadge}
            styles={{ root: badgeRootStyle }}
          >
            {chip.line}
          </Badge>
        );
      })}
    </Group>
  );
}

export function SessionTableRow({
  session,
  onSessionClick,
}: SessionTableRowProps) {
  const hasIssues = session.issues.length > 0;
  const hasQuality =
    session.qualityScore != null && Number.isFinite(session.qualityScore);
  const topIssues = sortIssuesBySeverity(session.issues).slice(
    0,
    ISSUES_DISPLAY_LIMIT,
  );

  const criticalChips = getCriticalInteractionChips(session);
  const criticalTooltipLines = getCriticalInteractionsTooltipLines(session);
  const hasCriticalData = criticalChips.length > 0;
  const tooltipHasMoreDetail =
    criticalTooltipLines.length > IMPACTED_SCREENS_DISPLAY_LIMIT;
  const tooltipLabel = criticalTooltipLines.join("\n");
  const showTooltip =
    hasCriticalData &&
    tooltipLabel.length > 0 &&
    (tooltipHasMoreDetail ||
      criticalTooltipLines.join("\n") !==
        criticalChips
          .map((c) => (c.kind === "pulse" ? c.name : c.line))
          .join("\n"));

  return (
    <div
      className={`${gridClasses.dataRow} ${sessionClasses.tableRow}`}
      role="row"
      tabIndex={0}
      onClick={() => onSessionClick(session.sessionId)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSessionClick(session.sessionId);
        }
      }}
    >
      <div className={gridClasses.cell} role="gridcell">
        <Text size="sm" ta="left" className={gridClasses.cellTextNatural}>
          {formatTimestamp(session.startTime)}
        </Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Text size="sm" ta="left" className={gridClasses.cellTextNatural}>
          {formatDuration(session.durationMs)}
        </Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Text size="sm" ta="left" className={gridClasses.cellTextNatural}>
          {session.user ?? SESSION_LIST_LABELS.anonymousUser}
        </Text>
      </div>
      <div
        className={`${gridClasses.cell} ${gridClasses.cellQuality}`}
        role="gridcell"
      >
        <Text
          size="sm"
          ta="left"
          className={
            !hasQuality
              ? `${gridClasses.cellTextNatural} ${sessionClasses.qualityNa}`
              : gridClasses.cellTextNatural
          }
          fw={hasQuality ? 600 : undefined}
          c={
            hasQuality
              ? getQualityColor(session.qualityScore as number)
              : undefined
          }
          styles={{
            root: {
              textAlign: "left",
            },
          }}
        >
          {hasQuality
            ? (session.qualityScore as number).toFixed(2)
            : SESSION_LIST_LABELS.noQuality}
        </Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Badge
          size="sm"
          variant="light"
          color={getPlatformColor(session.platform)}
          styles={{ root: badgeRootStyle }}
        >
          {session.platform}
        </Badge>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        {!hasIssues ? (
          <Badge
            color="teal"
            variant="light"
            size="sm"
            styles={{ root: badgeRootStyle }}
          >
            {SESSION_LIST_LABELS.clean}
          </Badge>
        ) : (
          <Group gap={4} justify="flex-start" align="flex-start" wrap="wrap">
            {topIssues.map((issue) => (
              <Badge
                key={issue.type}
                color={getIssueBadgeColor(issue.type)}
                variant={issue.type === "CRASH" ? "filled" : "light"}
                size="sm"
                styles={{ root: badgeRootStyle }}
              >
                {issue.count > 1
                  ? `${issue.label} (${issue.count})`
                  : issue.label}
              </Badge>
            ))}
          </Group>
        )}
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <div className={gridClasses.criticalInteractionsCell}>
          {!hasCriticalData ? (
            <Text size="sm" c="dimmed" className={gridClasses.criticalEmpty}>
              {SESSION_LIST_LABELS.noCriticalInteractions}
            </Text>
          ) : showTooltip ? (
            <Tooltip
              label={tooltipLabel}
              multiline
              maw={320}
              events={{ hover: true, focus: true, touch: true }}
            >
              <div className={gridClasses.criticalTooltipAnchor}>
                <CriticalInteractionBadges chips={criticalChips} />
              </div>
            </Tooltip>
          ) : (
            <CriticalInteractionBadges chips={criticalChips} />
          )}
        </div>
      </div>
    </div>
  );
}
