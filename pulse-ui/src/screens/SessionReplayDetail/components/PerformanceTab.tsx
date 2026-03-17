import { Stack, Text } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface PerformanceTabProps {
  sessionData: SessionDetailData;
}

export function PerformanceTab({ sessionData }: PerformanceTabProps) {
  return (
    <Stack gap="md">
      <Text size="sm" c="dimmed">
        App vitals data will appear here once available.
      </Text>
    </Stack>
  );
}
