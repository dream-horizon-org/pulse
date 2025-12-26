import { Box, Text, Badge, Group, Paper } from "@mantine/core";
import {
  IconBug,
  IconAlertTriangle,
  IconExclamationCircle,
  IconCalendar,
  IconChartBar,
} from "@tabler/icons-react";
import classes from "./IssueDetailsCard.module.css";

interface IssueDetailsCardProps {
  issue: any;
  issueType: string;
  groupId: string;
}

export const IssueDetailsCard: React.FC<IssueDetailsCardProps> = ({
  issue,
  issueType,
  groupId,
}) => {
  const getIssueIcon = () => {
    switch (issueType.toLowerCase()) {
      case "crash":
        return <IconBug size={20} color="#ef4444" />;
      case "anr":
        return <IconAlertTriangle size={20} color="#f59e0b" />;
      case "non-fatal":
        return <IconExclamationCircle size={20} color="#3b82f6" />;
      default:
        return <IconBug size={20} color="#6b7280" />;
    }
  };

  const getIssueColor = () => {
    switch (issueType.toLowerCase()) {
      case "crash":
        return "red";
      case "anr":
        return "orange";
      case "non-fatal":
        return "blue";
      default:
        return "gray";
    }
  };

  return (
    <Paper className={classes.detailsCard}>
      <Group justify="space-between" align="center" wrap="nowrap">
        {/* Left: Icon, Type, and Error Message */}
        <Group gap="md" align="center" style={{ flex: 1, minWidth: 0 }}>
          {getIssueIcon()}
          <Badge size="md" variant="light" color={getIssueColor()}>
            {issueType}
          </Badge>
          {issue.issueType && (
            <Badge size="md" variant="outline" color="gray">
              {issue.issueType}
            </Badge>
          )}
          <Text className={classes.errorMessage}>
            {issue.errorMessage || issue.anrMessage || issue.message || groupId}
          </Text>
        </Group>

        {/* Right: Compact Stats */}
        <Group gap="xl" className={classes.compactStats}>
          <Box className={classes.compactStatItem}>
            <Group gap={6} align="center">
              <Text className={classes.compactStatLabel}>Version:</Text>
              <Badge size="sm" variant="light" className={classes.versionBadge}>
                {issue.appVersion}
              </Badge>
            </Group>
          </Box>
          <Box className={classes.compactStatItem}>
            <Group gap={6} align="center">
              <IconChartBar size={14} className={classes.statIcon} />
              <Text className={classes.compactStatValue}>
                {issue.occurrences.toLocaleString()}
              </Text>
              <Text className={classes.compactStatLabel}>occurrences</Text>
            </Group>
          </Box>
          <Box className={classes.compactStatItem}>
            <Group gap={6} align="center">
              <IconCalendar size={14} className={classes.statIcon} />
              <Text className={classes.compactStatValue}>
                {new Date(issue.firstSeen).toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                })}
              </Text>
              <Text className={classes.compactStatLabel}>→</Text>
              <Text className={classes.compactStatValue}>
                {new Date(issue.lastSeen).toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                })}
              </Text>
            </Group>
          </Box>
        </Group>
      </Group>
    </Paper>
  );
};
