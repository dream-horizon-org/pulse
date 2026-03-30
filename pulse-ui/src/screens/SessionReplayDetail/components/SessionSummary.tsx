import { Paper, Box, Text, Badge, Group, Tooltip } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import {
  LABELS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../constants/strings";
import {
  TABLE_COLUMN_LABELS,
  SESSION_LIST_LABELS,
} from "../../SessionReplaySessions/constants/sessionList.constants";
import {
  formatTimestamp,
  formatDuration,
  getQualityColor,
  getPlatformColor,
} from "../../SessionReplaySessions/utils/sessionListUtils";
import classes from "./SessionSummary.module.css";

interface SessionSummaryProps {
  sessionData: SessionDetailData;
}

export function SessionSummary({ sessionData }: SessionSummaryProps) {
  const quality = sessionData.interactionQuality;
  const hasQuality = quality != null && Number.isFinite(quality);

  return (
    <Paper className={classes.summary} withBorder p={0}>
      <Box className={classes.summaryBody}>
        <div className={classes.sessionIdBlock}>
          <Box component="div" className={classes.metricLabel}>
            {LABELS.SESSION_ID}
          </Box>
          <Text
            size="sm"
            fw={500}
            className={classes.metricValue}
            style={{ wordBreak: "break-all" }}
          >
            {sessionData.sessionId}
          </Text>
        </div>

        <div className={classes.metricsRow}>
          <div className={classes.metric}>
            <Box component="div" className={classes.metricLabel}>
              {LABELS.USER_ID}
            </Box>
            <div className={classes.metricValue}>
              {sessionData.isAnonymous ? (
                <Badge size="sm" variant="light" color="gray">
                  {SESSION_LIST_LABELS.anonymousUser}
                </Badge>
              ) : (
                <Group gap={6} wrap="wrap">
                  <Text component="span" size="sm" fw={500}>
                    {sessionData.userId}
                  </Text>
                  <Badge size="sm" variant="light" color="blue">
                    {STATUS_LABELS.IDENTIFIED_UPPERCASE}
                  </Badge>
                </Group>
              )}
            </div>
          </div>

          <div className={classes.metric}>
            <Box component="div" className={classes.metricLabel}>
              {TABLE_COLUMN_LABELS.startTime}
            </Box>
            <Text size="sm" fw={500} className={classes.metricValue}>
              {formatTimestamp(sessionData.startTime)}
            </Text>
          </div>

          <div className={classes.metric}>
            <Box component="div" className={classes.metricLabel}>
              {TABLE_COLUMN_LABELS.duration}
            </Box>
            <Text size="sm" fw={500} className={classes.metricValue}>
              {formatDuration(sessionData.duration)}
            </Text>
          </div>

          <div className={classes.metric}>
            <Box component="div" className={classes.metricLabel}>
              {TABLE_COLUMN_LABELS.quality}
            </Box>
            <div className={classes.metricValue}>
              {hasQuality ? (
                <Tooltip
                  label="Interaction quality on a 0–1 scale (same as the session list Quality column)"
                  position="top"
                  withArrow
                  openDelay={400}
                >
                  <Text
                    component="span"
                    size="sm"
                    fw={500}
                    c={getQualityColor(quality as number)}
                    style={{ cursor: "help" }}
                  >
                    {(quality as number).toFixed(2)}
                    <Text component="span" size="xs" c="dimmed" ml={6}>
                      (0–1)
                    </Text>
                  </Text>
                </Tooltip>
              ) : (
                <Text size="sm">{SESSION_LIST_LABELS.noQuality}</Text>
              )}
            </div>
          </div>

          <div className={classes.metric}>
            <Box component="div" className={classes.metricLabel}>
              {TABLE_COLUMN_LABELS.platform}
            </Box>
            <div className={classes.metricValue}>
              <Badge
                size="sm"
                variant="light"
                color={getPlatformColor(sessionData.platform)}
              >
                {sessionData.platform}
              </Badge>
            </div>
          </div>
        </div>
      </Box>
    </Paper>
  );
}
