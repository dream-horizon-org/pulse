import { Box, Badge, Card, Group, Stack, Text } from "@mantine/core";
import { IconDeviceMobile, IconPlayerPlay } from "@tabler/icons-react";
import type { SessionCardProps } from "./SessionCard.interface";
import classes from "./SessionCard.module.css";

/**
 * Session card for the Root Cause "Related Session Replays" section.
 * Displays session id, duration, relative time, device, failure summary, and a Watch Replay action.
 * The entire card is clickable when replayUrl or onWatchReplay is provided.
 */
export const SessionCard = ({
  sessionId,
  duration,
  relativeTime,
  device,
  failureSummary,
  replayUrl,
  onWatchReplay,
}: SessionCardProps) => {
  const canWatchReplay = !!replayUrl || !!onWatchReplay;
  const cardClassName = canWatchReplay
    ? `${classes.card} ${classes.cardClickable}`
    : classes.card;

  const content = (
    <Stack gap="sm">
      <div className={classes.sessionRow}>
        <Group gap="xs" wrap="nowrap">
          <IconPlayerPlay
            size={16}
            color="var(--mantine-color-teal-7)"
            aria-hidden
          />
          <Text className={classes.sessionId} component="span">
            {sessionId}
          </Text>
          <Badge
            size="sm"
            variant="light"
            color="teal"
            className={classes.durationBadge}
          >
            {duration}
          </Badge>
        </Group>
        <Text className={classes.relativeTime} component="span">
          {relativeTime}
        </Text>
      </div>

      <Group gap={6}>
        <IconDeviceMobile
          size={14}
          color="var(--mantine-color-gray-5)"
          aria-hidden
        />
        <Text className={classes.deviceText}>{device}</Text>
      </Group>

      {failureSummary.trim() !== "" && (
        <Box className={classes.failureSummaryBox}>{failureSummary}</Box>
      )}

      {canWatchReplay && (
        <span className={classes.watchReplayLink}>
          <Group gap="xs" wrap="nowrap" align="center">
            <IconPlayerPlay size={14} aria-hidden />
            <span>Watch Replay</span>
          </Group>
        </span>
      )}
    </Stack>
  );

  if (canWatchReplay && replayUrl) {
    return (
      <Card
        component="a"
        href={replayUrl}
        withBorder
        padding="md"
        className={cardClassName}
        radius="md"
        aria-label={`Watch session replay ${sessionId}`}
      >
        {content}
      </Card>
    );
  }

  if (canWatchReplay && onWatchReplay) {
    return (
      <Card
        component="button"
        type="button"
        onClick={onWatchReplay}
        withBorder
        padding="md"
        className={cardClassName}
        radius="md"
        aria-label={`Watch session replay ${sessionId}`}
      >
        {content}
      </Card>
    );
  }

  return (
    <Card withBorder padding="md" className={cardClassName} radius="md">
      {content}
    </Card>
  );
};
