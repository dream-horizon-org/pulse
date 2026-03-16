import { Group, Text, ActionIcon } from "@mantine/core";
import {
  IconZoomIn,
  IconZoomOut,
  IconMaximize,
} from "@tabler/icons-react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface PlayerHeaderProps {
  sessionData: SessionDetailData;
}

export function PlayerHeader({ sessionData }: PlayerHeaderProps) {
  return (
    <Group justify="space-between">
      <Group gap="xs">
        <Text size="sm" fw={500}>
          {sessionData.platform} {sessionData.device} · {sessionData.os}
        </Text>
      </Group>
      <Group gap="xs">
        <ActionIcon variant="subtle" size="sm" color="gray">
          <IconZoomIn size={16} />
        </ActionIcon>
        <ActionIcon variant="subtle" size="sm" color="gray">
          <IconZoomOut size={16} />
        </ActionIcon>
        <ActionIcon variant="subtle" size="sm" color="gray">
          <IconMaximize size={16} />
        </ActionIcon>
      </Group>
    </Group>
  );
}
