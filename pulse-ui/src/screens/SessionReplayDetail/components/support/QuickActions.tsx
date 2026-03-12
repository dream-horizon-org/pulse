import { Card, Text, Stack, Button } from "@mantine/core";
import {
  IconTicket,
  IconRocket,
  IconExclamationCircle,
} from "@tabler/icons-react";
import type { SupportContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS } from "../../constants/strings";

interface QuickActionsProps {
  suggestedActions: SupportContext["suggestedActions"];
}

export function QuickActions({ suggestedActions }: QuickActionsProps) {
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
        {HEADERS.QUICK_ACTIONS}
      </Text>

      <Stack gap="xs">
        {suggestedActions.map((action) => (
          <Button
            key={action.id}
            fullWidth
            variant={action.priority === "high" ? "filled" : "light"}
            color={action.priority === "high" ? "blue" : "gray"}
            leftSection={
              action.type === "create_ticket" ? (
                <IconTicket size={16} />
              ) : action.type === "send_workaround" ? (
                <IconRocket size={16} />
              ) : (
                <IconExclamationCircle size={16} />
              )
            }
          >
            {action.label}
          </Button>
        ))}
      </Stack>
    </Card>
  );
}
