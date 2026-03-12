import { Card, Text, Stack, Group, Badge } from "@mantine/core";
import type { SessionDetailData } from "../../../../services/sessionReplay/mockSessionDetail";
import { formatDuration, getQualityColor } from "../../utils/sessionUtils";
import {
  HEADERS,
  LABELS,
  STATUS_LABELS,
  FORMAT_STRINGS,
} from "../../constants/strings";

interface CustomerImpactProps {
  sessionData: SessionDetailData;
}

export function CustomerImpact({ sessionData }: CustomerImpactProps) {
  return (
    <Card padding="md" withBorder>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.CUSTOMER_IMPACT}
      </Text>

      <Stack gap="sm">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.USER_STATUS}
          </Text>
          <Badge color={sessionData.isAnonymous ? "gray" : "blue"}>
            {sessionData.isAnonymous
              ? STATUS_LABELS.ANONYMOUS
              : STATUS_LABELS.IDENTIFIED}
          </Badge>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.USER_ID}
          </Text>
          <Text size="sm" fw={500} ff="monospace">
            {sessionData.userId}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.SESSION_DURATION}
          </Text>
          <Text size="sm" fw={500}>
            {formatDuration(sessionData.duration)}
          </Text>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.SESSION_QUALITY}
          </Text>
          <Badge color={getQualityColor(sessionData.interactionQuality)}>
            {FORMAT_STRINGS.QUALITY_SCORE.replace(
              "{score}",
              sessionData.interactionQuality.toString(),
            )}
          </Badge>
        </Group>

        {sessionData.businessContext?.conversionValue && (
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.ATTEMPTED_TRANSACTION}
            </Text>
            <Text size="sm" fw={600} c="red">
              ${sessionData.businessContext.conversionValue}
            </Text>
          </Group>
        )}
      </Stack>
    </Card>
  );
}
