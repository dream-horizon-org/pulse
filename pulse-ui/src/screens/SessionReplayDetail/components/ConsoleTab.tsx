import {
  Badge,
  Box,
  Center,
  Group,
  Paper,
  SegmentedControl,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { IconTerminal2 } from "@tabler/icons-react";
import { useMemo, useState } from "react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import {
  TAB_PANEL_CONSOLE_EMPTY,
  TAB_PANEL_DESCRIPTION,
  TAB_PANEL_TITLE,
} from "../constants/strings";
import { ConsoleLogList } from "./ConsoleLogList";
import { ConsoleVisualization } from "./ConsoleVisualization";
import { SessionDetailTabPanel } from "./SessionDetailTabPanel";

type ConsoleView = "list" | "charts";

interface ConsoleTabProps {
  sessionData: SessionDetailData;
}

export function ConsoleTab({ sessionData }: ConsoleTabProps) {
  const { consoleLogs } = sessionData;
  const [view, setView] = useState<ConsoleView>("list");

  const sessionStart = useMemo(
    () => new Date(sessionData.startTime),
    [sessionData.startTime],
  );

  const counts = useMemo(() => {
    let err = 0;
    let warn = 0;
    let log = 0;
    for (const l of consoleLogs) {
      if (l.level === "error") err += 1;
      else if (l.level === "warn") warn += 1;
      else log += 1;
    }
    return { err, warn, log };
  }, [consoleLogs]);

  const hasLogs = consoleLogs.length > 0;
  const showCharts = hasLogs && consoleLogs.length >= 2;

  return (
    <SessionDetailTabPanel
      title={TAB_PANEL_TITLE.CONSOLE}
      description={TAB_PANEL_DESCRIPTION.CONSOLE}
      toolbar={
        hasLogs && showCharts ? (
          <SegmentedControl
            size="xs"
            value={view}
            onChange={(v) => setView(v as ConsoleView)}
            data={[
              { label: "Timeline", value: "list" },
              { label: "Charts", value: "charts" },
            ]}
          />
        ) : undefined
      }
    >
      {!hasLogs ? (
        <Paper withBorder p="xl" radius="md" bg="gray.0">
          <Center>
            <Stack align="center" gap="md" maw={420}>
              <ThemeIcon size={56} radius="md" variant="light" color="gray">
                <IconTerminal2 size={28} stroke={1.5} />
              </ThemeIcon>
              <Stack gap={6} align="center">
                <Title order={5} ta="center" fw={600}>
                  {TAB_PANEL_CONSOLE_EMPTY.title}
                </Title>
                <Text size="sm" c="dimmed" ta="center" lh={1.6}>
                  {TAB_PANEL_CONSOLE_EMPTY.body}
                </Text>
              </Stack>
            </Stack>
          </Center>
        </Paper>
      ) : (
        <Stack gap="sm" w="100%">
          <Group gap="xs" wrap="wrap">
            <Badge size="sm" variant="light" color="gray">
              {consoleLogs.length} line{consoleLogs.length === 1 ? "" : "s"}
            </Badge>
            {counts.log > 0 ? (
              <Badge size="sm" variant="light" color="gray">
                {counts.log} log
              </Badge>
            ) : null}
            {counts.warn > 0 ? (
              <Badge size="sm" variant="light" color="yellow">
                {counts.warn} warn
              </Badge>
            ) : null}
            {counts.err > 0 ? (
              <Badge size="sm" variant="light" color="red">
                {counts.err} error
              </Badge>
            ) : null}
          </Group>

          <Box w="100%" style={{ display: "flex", flexDirection: "column" }}>
            {view === "list" || !showCharts ? (
              <ConsoleLogList logs={consoleLogs} />
            ) : (
              <ConsoleVisualization
                consoleLogs={consoleLogs}
                sessionStartTime={sessionStart}
              />
            )}
          </Box>
        </Stack>
      )}
    </SessionDetailTabPanel>
  );
}
