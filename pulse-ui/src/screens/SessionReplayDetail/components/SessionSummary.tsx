import { Paper, Box, Text, Badge, Group, RingProgress } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { UserJourney } from "./all/UserJourney";
import { STATUS_LABELS_EXTENDED as STATUS_LABELS } from "../constants/strings";
import classes from "./SessionSummary.module.css";

function getQualityColor(score: number): string {
  if (score >= 8) return "var(--mantine-color-teal-6)";
  if (score >= 6) return "var(--mantine-color-yellow-6)";
  return "var(--mantine-color-red-6)";
}

interface SessionSummaryProps {
  sessionData: SessionDetailData;
}

export function SessionSummary({ sessionData }: SessionSummaryProps) {
  const quality = sessionData.interactionQuality;
  const hasQuality = quality != null && Number.isFinite(quality);

  const formattedTime = new Date(sessionData.startTime).toLocaleString(
    "en-US",
    {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    },
  );

  return (
    <Paper className={classes.summary} withBorder p={0}>
      {/* Single info row: most important first */}
      <Box className={classes.infoRow}>
        {/* 1. Session ID */}
        <Box className={classes.infoItem}>
          <Text size="sm" ff="monospace" c="dimmed">
            {sessionData.sessionId}
          </Text>
        </Box>

        <Box className={classes.dot} />

        {/* 2. User */}
        <Box className={classes.infoItem}>
          <Text size="md" fw={600}>
            {sessionData.userId || "Anonymous"}
          </Text>
          <Badge
            size="sm"
            variant="light"
            color={sessionData.isAnonymous ? "gray" : "blue"}
          >
            {sessionData.isAnonymous
              ? STATUS_LABELS.ANONYMOUS
              : STATUS_LABELS.IDENTIFIED_UPPERCASE}
          </Badge>
        </Box>

        <Box className={classes.dot} />

        {/* 3. Quality */}
        <Box className={classes.infoItem}>
          {hasQuality ? (
            <Group gap={6} align="center">
              <RingProgress
                size={28}
                thickness={3}
                roundCaps
                sections={[
                  {
                    value: (quality / 10) * 100,
                    color: getQualityColor(quality),
                  },
                ]}
              />
              <Text
                size="sm"
                fw={600}
                style={{ color: getQualityColor(quality) }}
              >
                {quality.toFixed(1)}
              </Text>
            </Group>
          ) : (
            <Text size="sm" fw={500} c="dimmed">
              Quality: NA
            </Text>
          )}
        </Box>

        <Box className={classes.dot} />

        {/* 4. Timestamp */}
        <Box className={classes.infoItem}>
          <Text size="sm" c="dimmed">
            {formattedTime}
          </Text>
        </Box>
      </Box>

      {/* Journey */}
      <Box className={classes.journeyBlock}>
        <UserJourney journey={sessionData.journey} />
      </Box>
    </Paper>
  );
}
