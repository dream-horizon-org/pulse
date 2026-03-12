import { Paper, Group, Box, Text, RingProgress } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { UserJourney } from "./all/UserJourney";
import { formatDuration } from "../utils/sessionUtils";
import { LABELS } from "../constants/strings";
import classes from "./SessionSummary.module.css";

function getQualityColor(score: number) {
  if (score >= 8) return "teal";
  if (score >= 6) return "yellow";
  return "red";
}

interface SessionSummaryProps {
  sessionData: SessionDetailData;
}

export function SessionSummary({ sessionData }: SessionSummaryProps) {
  const quality = sessionData.interactionQuality ?? 0;

  return (
    <Paper className={classes.summary} p="md" withBorder>
      <Box className={classes.summaryContent}>
        <Box className={classes.metricsRow}>
          <Box className={classes.qualityBlock}>
            <Text size="xs" fw={600} c="dimmed" tt="uppercase">
              {LABELS.QUALITY_SCORE}
            </Text>
            <Group gap="xs" align="center">
              <RingProgress
                size={48}
                thickness={5}
                sections={[
                  {
                    value: (quality / 10) * 100,
                    color: getQualityColor(quality),
                  },
                ]}
                label={
                  <Text size="xs" fw={700} ta="center">
                    {quality.toFixed(1)}
                  </Text>
                }
              />
            </Group>
          </Box>
          <Box className={classes.metricBlock}>
            <Text size="xs" fw={600} c="dimmed" tt="uppercase">
              {LABELS.DURATION}
            </Text>
            <Text size="sm" fw={500}>
              {formatDuration(sessionData.duration)}
            </Text>
          </Box>
        </Box>
        <Box className={classes.journeyBlock}>
          <UserJourney journey={sessionData.journey} />
        </Box>
      </Box>
    </Paper>
  );
}
