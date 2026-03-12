import { Box, Text, Card, Group, Badge, ScrollArea } from "@mantine/core";
import { IconChevronRight } from "@tabler/icons-react";
import { LABELS } from "../../constants/strings";

interface UserJourneyProps {
  journey: string[];
}

export function UserJourney({ journey }: UserJourneyProps) {
  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
        {LABELS.USER_JOURNEY}
      </Text>
      <Card padding="sm" withBorder>
        <ScrollArea>
          <Group gap="xs" wrap="nowrap">
            {journey.map((path, idx) => {
              const isError = path.toLowerCase().includes("error");
              const displayPath = path.startsWith("/")
                ? path.toUpperCase()
                : path.toUpperCase();
              return (
                <Group key={idx} gap={4} wrap="nowrap">
                  <Badge
                    variant={isError ? "filled" : "light"}
                    size="sm"
                    color={isError ? "red" : "blue"}
                  >
                    {displayPath}
                  </Badge>
                  {idx < journey.length - 1 && (
                    <IconChevronRight size={12} />
                  )}
                </Group>
              );
            })}
          </Group>
        </ScrollArea>
      </Card>
    </Box>
  );
}
