import { Paper, Box, Text, Group } from "@mantine/core";
import { IconDeviceMobile } from "@tabler/icons-react";
import { SessionTimelineEvent } from "../../SessionTimeline.interface";
import { extractResourceAttributes } from "./utils/extractors";
import { buildResourceInfoItems } from "./utils/resourceMappings";
import classes from "./ResourceAttributesPanel.module.css";

interface ResourceAttributesPanelProps {
  events: SessionTimelineEvent[];
}

export function ResourceAttributesPanel({
  events,
}: ResourceAttributesPanelProps) {
  const resourceAttrs = extractResourceAttributes(events);
  const infoItems = buildResourceInfoItems(resourceAttrs);

  if (infoItems.length === 0) {
    return null;
  }

  return (
    <Paper className={classes.resourceCard} mb="md">
      <Box className={classes.topAccent} />
      <Group gap="sm" mb="md">
        <IconDeviceMobile size={20} color="#0ba09a" />
        <Text className={classes.cardTitle}>Device Information</Text>
      </Group>
      <Box className={classes.metricsGrid}>
        {infoItems.map((item) => (
          <Box key={item.key} className={classes.metricCard}>
            <Text className={classes.metricLabel}>{item.label}</Text>
            <Text className={classes.metricValue} style={{ color: "#0ba09a" }}>
              {item.value}
            </Text>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}
