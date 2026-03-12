import { Stack, Group, SegmentedControl, Timeline, Box, Text, Badge } from "@mantine/core";
import { EventsVisualization } from "./EventsVisualization";
import { formatTimestamp } from "../utils/sessionUtils";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface EventsTabProps {
  sessionData: SessionDetailData;
  viewMode: "text" | "graph";
  onViewModeChange: (mode: "text" | "graph") => void;
}

export function EventsTab({
  sessionData,
  viewMode,
  onViewModeChange,
}: EventsTabProps) {
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
        <EventsVisualization
          events={sessionData.events}
          sessionStartTime={new Date(sessionData.startTime)}
        />
      ) : (
        <Timeline
          active={sessionData.events.length}
          bulletSize={20}
          lineWidth={2}
        >
          {sessionData.events.map((event, idx) => (
            <Timeline.Item
              key={idx}
              bullet={
                <Box
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: "50%",
                    background: "var(--mantine-color-teal-6)",
                  }}
                />
              }
            >
              <Text size="xs" c="dimmed">
                {formatTimestamp(
                  event.timestamp,
                  new Date(sessionData.startTime),
                )}
              </Text>
              <Text size="sm" fw={500}>
                {event.description}
              </Text>
              <Badge size="xs" variant="light" mt={4}>
                {event.type}
              </Badge>
            </Timeline.Item>
          ))}
        </Timeline>
      )}
    </Stack>
  );
}
