import { Group, Text } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface PlayerHeaderProps {
  sessionData: SessionDetailData;
}

export function PlayerHeader({ sessionData }: PlayerHeaderProps) {
  return (
    <Group justify="space-between" wrap="wrap" gap="xs">
      <Text size="sm" fw={500} lineClamp={2} style={{ minWidth: 0 }}>
        {sessionData.platform} {sessionData.device} · {sessionData.os}
      </Text>
    </Group>
  );
}
