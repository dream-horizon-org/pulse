import { Card, Text, Group, Stack, Badge, Button } from "@mantine/core";
import { IconExternalLink } from "@tabler/icons-react";
import type { TechnicalContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, LABELS, BUTTON_LABELS } from "../../constants/strings";

interface ErrorGroupInfoProps {
  errorGroupInfo: NonNullable<TechnicalContext["errorGroupInfo"]>;
}

export function ErrorGroupInfoComponent({
  errorGroupInfo,
}: ErrorGroupInfoProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.ERROR_GROUP_INFO}
        </Text>
        <Badge color="red" variant="filled">
          #{errorGroupInfo.groupId.split("_")[2]}
        </Badge>
      </Group>

      <Stack gap="sm">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.OCCURRENCES}
          </Text>
          <Text size="sm" fw={600}>
            {errorGroupInfo.occurrenceCount}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.AFFECTED_USERS}
          </Text>
          <Text size="sm" fw={600}>
            {errorGroupInfo.affectedUsers}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.FIRST_SEEN}
          </Text>
          <Text size="sm">
            {new Date(errorGroupInfo.firstSeen).toLocaleString()}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.TREND}
          </Text>
          <Badge
            color={
              errorGroupInfo.trend === "increasing"
                ? "red"
                : errorGroupInfo.trend === "decreasing"
                  ? "teal"
                  : "gray"
            }
          >
            {errorGroupInfo.trend}
          </Badge>
        </Group>
      </Stack>

      <Button
        variant="light"
        fullWidth
        mt="md"
        leftSection={<IconExternalLink size={16} />}
      >
        {BUTTON_LABELS.VIEW_ALL_OCCURRENCES}
      </Button>
    </Card>
  );
}
