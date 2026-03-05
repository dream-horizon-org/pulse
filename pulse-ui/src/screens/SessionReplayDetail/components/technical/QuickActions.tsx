import { Card, Text, Stack, Button } from "@mantine/core";
import {
  IconBug,
  IconGitBranch,
  IconExternalLink,
} from "@tabler/icons-react";
import { HEADERS, BUTTON_LABELS } from "../../constants/strings";

export function QuickActions() {
  return (
    <Card
      padding="md"
      withBorder
      style={{
        position: "sticky",
        bottom: 0,
        backgroundColor: "var(--mantine-color-body)",
      }}
    >
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.ENGINEERING_ACTIONS}
      </Text>

      <Stack gap="xs">
        <Button
          variant="filled"
          color="orange"
          leftSection={<IconBug size={16} />}
        >
          {BUTTON_LABELS.CREATE_JIRA_TICKET}
        </Button>
        <Button variant="light" leftSection={<IconGitBranch size={16} />}>
          {BUTTON_LABELS.LINK_TO_PR}
        </Button>
        <Button variant="light" leftSection={<IconExternalLink size={16} />}>
          {BUTTON_LABELS.VIEW_ERROR_GROUP}
        </Button>
      </Stack>
    </Card>
  );
}
