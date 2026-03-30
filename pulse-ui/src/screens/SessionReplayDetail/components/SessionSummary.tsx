import {
  Paper,
  Box,
  Text,
  Badge,
  Group,
  RingProgress,
  Stack,
} from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import {
  LABELS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../constants/strings";
import { getQualityColor } from "../utils/sessionUtils";
import classes from "./SessionSummary.module.css";

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

  const qualityColor = hasQuality
    ? getQualityColor(quality as number)
    : undefined;

  return (
    <Paper className={classes.summary} withBorder p={0}>
      <Box className={classes.infoRow}>
        <Stack gap={6} className={classes.idsBlock}>
          <Text size="sm" className={classes.idLine}>
            <Text component="span" c="dimmed" fw={500}>
              {LABELS.SESSION_ID}:
            </Text>{" "}
            <Text
              component="span"
              ff="monospace"
              style={{ wordBreak: "break-all" }}
            >
              {sessionData.sessionId}
            </Text>
          </Text>
          <Group gap="xs" align="center" wrap="wrap" className={classes.idLine}>
            <Text size="sm">
              <Text component="span" c="dimmed" fw={500}>
                {LABELS.USER_ID}:
              </Text>{" "}
              <Text component="span" fw={600}>
                {sessionData.userId || "Anonymous"}
              </Text>
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
          </Group>
        </Stack>

        <Group gap="lg" wrap="wrap" className={classes.metricsGroup}>
          <Box className={classes.infoItem}>
            {hasQuality ? (
              <Group gap={6} align="center">
                <RingProgress
                  size={28}
                  thickness={3}
                  roundCaps
                  sections={[
                    {
                      value: Math.min(
                        100,
                        Math.max(0, (quality as number) * 100),
                      ),
                      color: qualityColor ?? "gray",
                    },
                  ]}
                />
                <Text size="sm" fw={600} c={qualityColor}>
                  {(quality as number).toFixed(2)}
                </Text>
              </Group>
            ) : (
              <Text size="sm" fw={500} c="dark.9">
                {LABELS.SESSION_QUALITY}: NA
              </Text>
            )}
          </Box>

          <Box className={classes.infoItem}>
            <Text size="sm" c="dimmed">
              {formattedTime}
            </Text>
          </Box>
        </Group>
      </Box>
    </Paper>
  );
}
