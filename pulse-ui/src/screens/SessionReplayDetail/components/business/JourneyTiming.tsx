import { Card, Text, Group, Progress, Stack, Divider, Badge } from "@mantine/core";
import { IconClock, IconChevronRight } from "@tabler/icons-react";
import { formatDuration } from "../../utils/sessionUtils";
import { HEADERS, LABELS, MESSAGES } from "../../constants/strings";

interface JourneyTimingProps {
  actualDuration: number;
  expectedDuration: number;
  journey: string[];
}

export function JourneyTiming({
  actualDuration,
  expectedDuration,
  journey,
}: JourneyTimingProps) {
  const durationDiff =
    ((actualDuration - expectedDuration) / expectedDuration) * 100;

  return (
    <Card padding="md" withBorder>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.JOURNEY_TIMING}
      </Text>

      <Stack gap="md">
        <Group justify="space-between">
          <Group gap="xs">
            <IconClock size={16} />
            <Text size="sm">{LABELS.ACTUAL_DURATION}</Text>
          </Group>
          <Text size="sm" fw={600}>
            {formatDuration(actualDuration)}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.EXPECTED_DURATION}
          </Text>
          <Text size="sm" c="dimmed">
            {formatDuration(expectedDuration)}
          </Text>
        </Group>

        <Progress
          value={Math.min((actualDuration / expectedDuration) * 100, 200)}
          color={durationDiff > 50 ? "red" : durationDiff > 20 ? "yellow" : "teal"}
          size="lg"
        />

        {Math.abs(durationDiff) > 10 && (
          <Text size="xs" c={durationDiff > 0 ? "red" : "teal"}>
            {durationDiff > 0 ? "+" : ""}
            {durationDiff.toFixed(0)}%{" "}
            {durationDiff > 0
              ? MESSAGES.SLOWER_THAN_EXPECTED
              : MESSAGES.FASTER_THAN_EXPECTED}{" "}
            {MESSAGES.THAN_EXPECTED}
          </Text>
        )}

        {/* Journey Path */}
        <Divider />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {LABELS.USER_JOURNEY}
        </Text>
        <Group gap={4} wrap="nowrap" style={{ overflowX: "auto" }}>
          {journey.map((path, idx) => (
            <Group key={idx} gap={4} wrap="nowrap">
              <Badge
                variant={idx === journey.length - 1 ? "filled" : "light"}
                color={path.includes("error") ? "red" : "blue"}
                size="sm"
              >
                {path}
              </Badge>
              {idx < journey.length - 1 && (
                <IconChevronRight size={12} color="gray" />
              )}
            </Group>
          ))}
        </Group>
      </Stack>
    </Card>
  );
}
