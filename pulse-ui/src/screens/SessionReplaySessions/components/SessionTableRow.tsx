import { Table, Text, Badge, Group, ActionIcon, Tooltip } from "@mantine/core";
import { IconVideo, IconExternalLink } from "@tabler/icons-react";
import type { SessionItem } from "../../../services/sessionReplay";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
  formatJourneyPreview,
  formatJourneyTooltip,
} from "../utils/sessionListUtils";
import classes from "../SessionReplaySessions.module.css";

export interface SessionTableRowProps {
  session: SessionItem;
  onWatch: (sessionId: string) => void;
  onOpenInNewTab: (sessionId: string) => void;
}

export function SessionTableRow({
  session,
  onWatch,
  onOpenInNewTab,
}: SessionTableRowProps) {
  const hasIssues =
    session.networkErrors > 0 ||
    session.interactionErrors > 0 ||
    session.crashCount > 0 ||
    session.anrCount > 0 ||
    session.nonFatal > 0 ||
    session.slowInteractionCount > 0 ||
    session.frozenFrameCount > 0;
  const quality = session.qualityScore ?? 0;

  return (
    <Table.Tr className={classes.tableRow}>
      <Table.Td>
        <Text size="sm">{formatTimestamp(session.startTime)}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{formatDuration(session.durationMs)}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{session.user ?? SESSION_LIST_LABELS.anonymousUser}</Text>
      </Table.Td>
      <Table.Td>
        <Text
          size="sm"
          fw={600}
          c={getQualityColor(quality)}
        >
          {session.qualityScore != null
            ? session.qualityScore.toFixed(2)
            : SESSION_LIST_LABELS.noQuality}
        </Text>
      </Table.Td>
      <Table.Td>
        {!hasIssues ? (
          <Badge color="teal" variant="light" size="sm">
            {SESSION_LIST_LABELS.clean}
          </Badge>
        ) : (
          <Group gap={4} style={{ flexWrap: "wrap" }}>
            {session.crashCount > 0 && (
              <Badge color="red" variant="filled" size="sm">
                {SESSION_LIST_LABELS.crashed}
              </Badge>
            )}
            {session.interactionErrors > 0 && (
              <Badge color="red" variant="light" size="sm">
                {SESSION_LIST_LABELS.failed}
              </Badge>
            )}
            {(session.networkErrors > 0 || session.nonFatal > 0) && (
              <Badge color="orange" variant="light" size="sm">
                {session.networkErrors + session.nonFatal}{" "}
                {session.networkErrors + session.nonFatal > 1
                  ? SESSION_LIST_LABELS.errors
                  : SESSION_LIST_LABELS.error}
              </Badge>
            )}
            {session.slowInteractionCount > 0 && (
              <Badge color="yellow" variant="light" size="sm">
                {SESSION_LIST_LABELS.slow}
              </Badge>
            )}
          </Group>
        )}
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
        <Tooltip label={formatJourneyTooltip(session.journey)}>
          <Text size="xs" c="dimmed" className={classes.journey}>
            {formatJourneyPreview(session.journey)}
          </Text>
        </Tooltip>
      </Table.Td>
      <Table.Td>
        <Group gap={4}>
          <Tooltip label={SESSION_LIST_LABELS.watchSession}>
            <ActionIcon
              variant="light"
              color="teal"
              onClick={() => onWatch(session.sessionId)}
            >
              <IconVideo size={16} />
            </ActionIcon>
          </Tooltip>
          <Tooltip label={SESSION_LIST_LABELS.openInNewTab}>
            <ActionIcon
              variant="subtle"
              color="gray"
              onClick={() => onOpenInNewTab(session.sessionId)}
            >
              <IconExternalLink size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>
      </Table.Td>
    </Table.Tr>
  );
}
