import { Box, Text, Paper, ActionIcon } from "@mantine/core";
import { IconVideo, IconPlayerPlay } from "@tabler/icons-react";
import classes from "./SessionReplayCard.module.css";

interface SessionReplayCardProps {
  duration?: string;
  sessionId: string;
  onClick?: () => void;
}

export const SessionReplayCard: React.FC<SessionReplayCardProps> = ({
  duration = "2:30",
  sessionId,
  onClick,
}) => {
  return (
    <Paper
      className={classes.replayCard}
      onClick={onClick}
      style={{ cursor: onClick ? "pointer" : "default" }}
    >
      <Box className={classes.topAccent} />
      <Text className={classes.cardTitle}>Session Replay</Text>

      <Box className={classes.videoPlaceholder}>
        <Box className={classes.playButtonWrapper}>
          <IconVideo size={50} className={classes.videoIcon} />
          {onClick && (
            <ActionIcon
              className={classes.playButton}
              size="xl"
              radius="xl"
              variant="filled"
              color="teal"
            >
              <IconPlayerPlay size={40} />
            </ActionIcon>
          )}
        </Box>
        <Text className={classes.recordingText}>Session Recording</Text>
        <Text className={classes.durationText}>{duration} duration</Text>
        {sessionId && (
          <Text className={classes.sessionIdText}>ID: {sessionId}</Text>
        )}
      </Box>
    </Paper>
  );
};
