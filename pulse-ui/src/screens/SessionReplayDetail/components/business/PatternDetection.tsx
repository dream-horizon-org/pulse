import { Card, Text, Group, SimpleGrid, RingProgress, Button } from "@mantine/core";
import { IconUsers, IconTarget } from "@tabler/icons-react";
import type { BusinessContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, LABELS, BUTTON_LABELS } from "../../constants/strings";

interface PatternDetectionProps {
  businessContext: BusinessContext;
}

export function PatternDetection({ businessContext }: PatternDetectionProps) {
  if (!businessContext.similarSessionsCount && !businessContext.similarErrorsToday) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Group mb="md">
        <IconUsers size={18} />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.PATTERN_DETECTION}
        </Text>
      </Group>

      <SimpleGrid cols={2} spacing="md">
        {businessContext.similarSessionsCount && (
          <Card padding="sm" withBorder>
            <RingProgress
              size={80}
              thickness={8}
              sections={[{ value: 100, color: "violet" }]}
              label={
                <Text size="lg" fw={700} ta="center">
                  {businessContext.similarSessionsCount}
                </Text>
              }
              mb="xs"
            />
            <Text size="xs" ta="center" c="dimmed">
              {LABELS.SIMILAR_SESSIONS_TODAY}
            </Text>
          </Card>
        )}

        {businessContext.similarErrorsToday &&
          businessContext.similarErrorsToday > 0 && (
            <Card padding="sm" withBorder>
              <RingProgress
                size={80}
                thickness={8}
                sections={[{ value: 100, color: "red" }]}
                label={
                  <Text size="lg" fw={700} ta="center">
                    {businessContext.similarErrorsToday}
                  </Text>
                }
                mb="xs"
              />
              <Text size="xs" ta="center" c="dimmed">
                {LABELS.SAME_ERROR_TODAY}
              </Text>
            </Card>
          )}
      </SimpleGrid>

      <Button
        variant="light"
        fullWidth
        mt="md"
        leftSection={<IconTarget size={16} />}
      >
        {BUTTON_LABELS.VIEW_SIMILAR_SESSIONS}
      </Button>
    </Card>
  );
}
