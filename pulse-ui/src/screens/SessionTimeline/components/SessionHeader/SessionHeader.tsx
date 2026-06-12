import { Paper, Box, Text, Group } from "@mantine/core";
import { IconActivity } from "@tabler/icons-react";
import { SessionSummary } from "../../SessionTimeline.interface";
import { formatDuration } from "../../utils/formatters";
import classes from "./SessionHeader.module.css";

interface SessionHeaderProps {
  summary: SessionSummary;
}

const getStatusColor = (status: string): string => {
  if (status === "crashed") return "#ef4444";
  if (status === "active") return "#3b82f6";
  return "#0ba09a";
};

const SESSION_INFO_FIELDS: Array<{
  key: keyof SessionSummary;
  label: string;
  format?: (value: any) => string;
  getColor?: (value: any) => string;
}> = [
  {
    key: "status",
    label: "Status",
    format: (v) => v.toUpperCase(),
    getColor: (v) => getStatusColor(v),
  },
  { key: "platform", label: "Platform" },
  {
    key: "duration",
    label: "Duration",
    format: (v) => formatDuration(v),
  },
  {
    key: "totalEvents",
    label: "Total Events",
    format: (v) => v.toLocaleString(),
  },
  { key: "crashes", label: "Crashes" },
  { key: "anrs", label: "ANRs" },
  { key: "frozenFrames", label: "Frozen Frames" },
];

export function SessionHeader({ summary }: SessionHeaderProps) {
  return (
    <Paper className={classes.sessionCard} mb="md">
      <Box className={classes.topAccent} />
      <Group gap="sm" mb="md">
        <IconActivity size={20} color="#0ba09a" />
        <Text className={classes.cardTitle}>Session</Text>
        <Text className={classes.sessionId}>{summary.sessionId}</Text>
      </Group>
      <Box className={classes.metricsGrid}>
        {SESSION_INFO_FIELDS.map((field) => {
          const value = summary[field.key];
          const displayValue = field.format
            ? field.format(value)
            : String(value);
          const color = field.getColor ? field.getColor(value) : "#0ba09a";

          return (
            <Box key={field.key} className={classes.metricCard}>
              <Text className={classes.metricLabel}>{field.label}</Text>
              <Text className={classes.metricValue} style={{ color }}>
                {displayValue}
              </Text>
            </Box>
          );
        })}
      </Box>
    </Paper>
  );
}
