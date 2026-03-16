import { Stack, Timeline, Box, Text, Badge } from "@mantine/core";
import { formatTimestamp } from "../utils/sessionUtils";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface EventsTabProps {
  sessionData: SessionDetailData;
}

export function EventsTab({ sessionData }: EventsTabProps) {
  return (
    <Stack gap="xs">
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
    </Stack>
  );
}
