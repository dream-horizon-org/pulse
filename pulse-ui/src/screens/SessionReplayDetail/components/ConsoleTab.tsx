import { Stack, Group, SegmentedControl, Card, Badge, Text, Code } from "@mantine/core";
import { ConsoleVisualization } from "./ConsoleVisualization";
import { formatTimestamp } from "../utils/sessionUtils";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface ConsoleTabProps {
  sessionData: SessionDetailData;
  viewMode: "text" | "graph";
  onViewModeChange: (mode: "text" | "graph") => void;
}

export function ConsoleTab({
  sessionData,
  viewMode,
  onViewModeChange,
}: ConsoleTabProps) {
  return (
    <Stack gap="xs">
      <Group justify="flex-end">
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => onViewModeChange(value as "text" | "graph")}
          data={[
            { label: "Text", value: "text" },
            { label: "Graph", value: "graph" },
          ]}
        />
      </Group>
      {viewMode === "graph" ? (
        <ConsoleVisualization
          consoleLogs={sessionData.consoleLogs}
          sessionStartTime={new Date(sessionData.startTime)}
        />
      ) : (
        <>
          {sessionData.consoleLogs.map((log, idx) => (
            <Card key={idx} padding="xs" withBorder>
              <Group justify="space-between" mb={4}>
                <Badge
                  size="xs"
                  color={
                    log.level === "error"
                      ? "red"
                      : log.level === "warn"
                        ? "yellow"
                        : "gray"
                  }
                >
                  {log.level.toUpperCase()}
                </Badge>
                <Text size="xs" c="dimmed">
                  {formatTimestamp(
                    log.timestamp,
                    new Date(sessionData.startTime),
                  )}
                </Text>
              </Group>
              <Code block style={{ fontSize: 11 }}>
                {log.message}
              </Code>
              {log.stackTrace && (
                <Code
                  block
                  mt={4}
                  style={{ fontSize: 10 }}
                  c="red"
                >
                  {log.stackTrace}
                </Code>
              )}
            </Card>
          ))}
        </>
      )}
    </Stack>
  );
}
