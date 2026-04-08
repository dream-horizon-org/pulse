import { Box, Text, Timeline, Stack, Badge } from "@mantine/core";
import { LABELS } from "../../constants/strings";

interface UserJourneyProps {
  journey: string[];
  showSectionTitle?: boolean;
}

export function UserJourney({
  journey,
  showSectionTitle = true,
}: UserJourneyProps) {
  const displayPath = (path: string) => path.toUpperCase();

  if (!journey.length) {
    return (
      <Box>
        {showSectionTitle && (
          <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
            {LABELS.USER_JOURNEY}
          </Text>
        )}
        <Text size="sm" c="dimmed">
          No journey data
        </Text>
      </Box>
    );
  }

  return (
    <Box>
      {showSectionTitle && (
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
          {LABELS.USER_JOURNEY}
        </Text>
      )}
      <Stack gap="xs">
        <Timeline active={journey.length} bulletSize={20} lineWidth={2}>
          {journey.map((path, idx) => {
            const isError = path.toLowerCase().includes("error");
            return (
              <Timeline.Item
                key={idx}
                bullet={
                  <Box
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: "50%",
                      background: isError
                        ? "var(--mantine-color-red-6)"
                        : "var(--mantine-color-teal-6)",
                    }}
                  />
                }
              >
                <Text size="xs" c="dimmed">
                  Step {idx + 1} of {journey.length}
                </Text>
                <Text size="sm" fw={500}>
                  {displayPath(path)}
                </Text>
                <Badge
                  size="xs"
                  variant="light"
                  mt={4}
                  color={isError ? "red" : "blue"}
                >
                  SCREEN
                </Badge>
              </Timeline.Item>
            );
          })}
        </Timeline>
      </Stack>
    </Box>
  );
}
