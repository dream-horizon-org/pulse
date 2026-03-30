import { Text, Badge, Group, Tooltip } from "@mantine/core";
import type { SessionItem } from "../../../services/sessionReplay";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  getIssueBadgeColor,
  formatImpactedScreensPreview,
  formatImpactedScreensTooltip,
} from "../utils/sessionListUtils";
import sessionClasses from "../SessionReplaySessions.module.css";
import gridClasses from "./SessionsTable.module.css";

export interface SessionTableRowProps {
  session: SessionItem;
  onSessionClick: (sessionId: string) => void;
}

export function SessionTableRow({
  session,
  onSessionClick,
}: SessionTableRowProps) {
  const hasIssues = session.issues.length > 0;
  const hasQuality =
    session.qualityScore != null && Number.isFinite(session.qualityScore);

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
        <Text size="sm">{formatTimestamp(session.startTime)}</Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Text size="sm">{formatDuration(session.durationMs)}</Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Text size="sm">
          {session.user ?? SESSION_LIST_LABELS.anonymousUser}
        </Text>
      </div>
      <div className={gridClasses.cell} role="gridcell">
        <Text
          size="sm"
          fw={hasQuality ? 600 : undefined}
          className={!hasQuality ? sessionClasses.qualityNa : undefined}
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
      <div className={gridClasses.cell} role="gridcell">
        <Badge
          size="sm"
          variant="light"
          color={getPlatformColor(session.platform)}
        >
          {session.platform}
        </Badge>
      </div>
      <div className={gridClasses.cell} role="gridcell">
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
      <div className={gridClasses.cell} role="gridcell">
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
            className={gridClasses.impactedInteractionsText}
          >
            {formatImpactedScreensPreview(session.impactedScreens)}
          </Text>
        </Tooltip>
      </div>
    </div>
  );
}
