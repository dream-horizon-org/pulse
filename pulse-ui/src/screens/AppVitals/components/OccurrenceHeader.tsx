import { Box, Text, Paper, Badge, Group } from "@mantine/core";
import { IconClock } from "@tabler/icons-react";
import { AppStateInfo } from "./AppStateInfo";
import classes from "./OccurrenceHeader.module.css";

interface OccurrenceHeaderProps {
  timestamp: Date;
  issueId: string;
  occurrenceId: string;
  occurrenceData: any;
}

export const OccurrenceHeader: React.FC<OccurrenceHeaderProps> = ({
  timestamp,
  issueId,
  occurrenceId,
  occurrenceData,
}) => {
  return (
    <Paper className={classes.headerCard}>
      <Box className={classes.topAccent} />

      <Group justify="space-between" align="center" wrap="nowrap">
        {/* Left: Title, Timestamp, and Badges */}
        <Group gap="md" align="center">
          <IconClock size={20} color="#0ba09a" />
          <Text className={classes.title}>Occurrence Details</Text>
          <Text className={classes.timestamp}>
            {timestamp.toLocaleString()}
          </Text>
          <Badge size="md" variant="outline" color="gray">
            Issue: {issueId}
          </Badge>
          <Badge size="md" variant="light" color="teal">
            Occurrence: {parseInt(occurrenceId) + 1}
          </Badge>
        </Group>

        {/* Right: App State */}
        <AppStateInfo appState={occurrenceData.appState} />
      </Group>
    </Paper>
  );
};
