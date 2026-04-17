import {
  Paper,
  Box,
  Text,
  Badge,
  Group,
  Stack,
  SimpleGrid,
} from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import {
  LABELS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../constants/strings";
import {
  formatTimestamp,
  getQualityColor,
  getPlatformColor,
} from "../../SessionReplaySessions/utils/sessionListUtils";
import { formatDuration } from "../utils/sessionUtils";
import classes from "./SessionSummary.module.css";

interface SessionSummaryProps {
  sessionData: SessionDetailData;
}

export function SessionSummary({ sessionData }: SessionSummaryProps) {
  const quality = sessionData.interactionQuality;
  const hasQuality = quality != null && Number.isFinite(quality);

  const formattedStart = formatTimestamp(sessionData.startTime);

  const qualityColor = hasQuality
    ? getQualityColor(quality as number)
    : undefined;

  return (
    <Paper className={classes.summary} withBorder p={0}>
      <Stack gap="md" p="md" className={classes.summaryStack}>
        <Box className={classes.headerSection}>
          <Text size="xs" c="dimmed" fw={500} mb={4}>
            {LABELS.SESSION_ID}
          </Text>
          <Text
            fw={700}
            size="lg"
            ff="monospace"
            style={{ wordBreak: "break-all" }}
          >
            {sessionData.sessionId}
          </Text>
        </Box>

        <SimpleGrid
          cols={{ base: 1, xs: 2, sm: 3, md: 5 }}
          spacing={{ base: "md", md: "lg" }}
          verticalSpacing="md"
        >
          <Stack gap={4}>
            <Text size="xs" c="dimmed" fw={500}>
              {LABELS.USER_ID}
            </Text>
            <Group gap="xs" align="center" wrap="wrap">
              <Text fw={600} size="sm">
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
            </Group>
          </Stack>

          <Stack gap={4}>
            <Text size="xs" c="dimmed" fw={500}>
              {LABELS.START_TIME}
            </Text>
            <Text fw={600} size="sm">
              {formattedStart}
            </Text>
          </Stack>

          <Stack gap={4}>
            <Text size="xs" c="dimmed" fw={500}>
              {LABELS.DURATION}
            </Text>
            <Text fw={600} size="sm">
              {formatDuration(sessionData.duration)}
            </Text>
          </Stack>

          <Stack gap={4}>
            <Text size="xs" c="dimmed" fw={500}>
              {LABELS.QUALITY}
            </Text>
            {hasQuality ? (
              <Group gap={4} align="baseline" wrap="wrap">
                <Text size="sm" fw={700} c={qualityColor}>
                  {(quality as number).toFixed(2)}
                </Text>
                <Text size="xs" c="dimmed" component="span">
                  {LABELS.QUALITY_RANGE_HINT}
                </Text>
              </Group>
            ) : (
              <Text size="sm" fw={600} c="dimmed">
                NA
              </Text>
            )}
          </Stack>

          <Stack gap={4}>
            <Text size="xs" c="dimmed" fw={500}>
              {LABELS.PLATFORM}
            </Text>
            <Badge
              size="sm"
              variant="light"
              color={getPlatformColor(sessionData.platform)}
              tt="uppercase"
              w="fit-content"
            >
              {sessionData.platform}
            </Badge>
          </Stack>
        </SimpleGrid>
      </Stack>
    </Paper>
  );
}
