import { Group, Text } from "@mantine/core";
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
    </Group>
  );
}
