import { Card, Text, Group, Stack, Badge } from "@mantine/core";
import type { BusinessContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, LABELS, STATUS_LABELS, MESSAGES } from "../../constants/strings";

interface UserSegmentationProps {
  businessContext: BusinessContext;
}

export function UserSegmentation({ businessContext }: UserSegmentationProps) {
  return (
    <Card padding="md" withBorder>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.USER_SEGMENTATION}
      </Text>

      <Stack gap="sm">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.SEGMENT}
          </Text>
          <Badge color="blue" variant="light">
            {businessContext.userSegment || MESSAGES.UNKNOWN}
          </Badge>
        </Group>

        {businessContext.cohort && (
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.COHORT}
            </Text>
            <Text size="sm" fw={500}>
              {businessContext.cohort}
            </Text>
          </Group>
        )}

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.SESSION_TYPE}
          </Text>
          <Badge color={businessContext.isFirstSession ? "orange" : "teal"}>
            {businessContext.isFirstSession
              ? STATUS_LABELS.FIRST_SESSION
              : STATUS_LABELS.RETURNING_USER}
          </Badge>
        </Group>

        {businessContext.lifetimeValue !== undefined && (
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.LIFETIME_VALUE}
            </Text>
            <Text size="sm" fw={600}>
              ${businessContext.lifetimeValue.toFixed(2)}
            </Text>
          </Group>
        )}
      </Stack>
    </Card>
  );
}
