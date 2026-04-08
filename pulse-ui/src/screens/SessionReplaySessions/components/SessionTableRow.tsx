import { Table, Text, Badge, Group, Tooltip } from "@mantine/core";
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
  formatImpactedInteractionsCellTooltip,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";

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
  const interactionNames =
    session.impactedInteractionNames?.filter(Boolean) ?? [];
  const hasInteractionPills = interactionNames.length > 0;
  const pathPreview = formatImpactedScreensPreview(session.impactedScreens);
  const hasPathSummary = pathPreview !== SESSION_LIST_LABELS.noImpactedScreens;

  return (
    <Table.Tr
      className={classes.tableRow}
      tabIndex={0}
      role="link"
      onClick={() => onSessionClick(session.sessionId)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSessionClick(session.sessionId);
        }
      }}
    >
      <Table.Td>
        <Text size="sm">{formatTimestamp(session.startTime)}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{formatDuration(session.durationMs)}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">
          {session.user ?? SESSION_LIST_LABELS.anonymousUser}
        </Text>
      </Table.Td>
      <Table.Td>
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
      </Table.Td>
      <Table.Td>
        <Badge
          size="sm"
          variant="light"
          color={getPlatformColor(session.platform)}
        >
          {session.platform}
        </Badge>
      </Table.Td>
      <Table.Td>
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
      </Table.Td>
      <Table.Td>
        {hasInteractionPills ? (
          <Tooltip
            label={formatImpactedInteractionsCellTooltip(
              interactionNames,
              session.impactedScreens,
            )}
            multiline
            maw={320}
          >
            <Group gap={6} style={{ flexWrap: "wrap" }}>
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
      </Table.Td>
    </Table.Tr>
  );
}
