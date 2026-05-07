import { Box, Group, Select, Text } from "@mantine/core";
import { IconCalendar } from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { SessionQualityRca } from "./SessionQualityRca";
import classes from "./SessionQualityRcaPage.module.css";

const DATE_OPTIONS = [
  { value: "0", label: "Today" },
  { value: "1", label: "Yesterday" },
  { value: "6", label: "Last 7 days" },
  { value: "13", label: "Last 14 days" },
  { value: "29", label: "Last 30 days" },
];

export function SessionQualityRcaPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [lookbackDays, setLookbackDays] = useState("6");

  const { date, asOfIso } = useMemo(() => {
    const now = new Date();
    const anchor = new Date(now);
    anchor.setDate(anchor.getDate() - parseInt(lookbackDays, 10));
    return { date: anchor.toISOString().slice(0, 10), asOfIso: now.toISOString() };
  }, [lookbackDays]);

  return (
    <Box className={classes.page}>
      <Group justify="space-between" align="center" mb="lg" wrap="wrap" gap="sm">
        <div>
          <Text size="xl" fw={700}>Session Quality RCA</Text>
          <Text size="sm" c="dimmed">
            Project-wide session quality root cause analysis
          </Text>
        </div>
        <Select
          leftSection={<IconCalendar size={16} />}
          value={lookbackDays}
          onChange={(v) => setLookbackDays(v ?? "6")}
          data={DATE_OPTIONS}
          style={{ minWidth: 150 }}
        />
      </Group>
      <SessionQualityRca
        projectId={projectId}
        date={date}
        asOfIso={asOfIso}
      />
    </Box>
  );
}
