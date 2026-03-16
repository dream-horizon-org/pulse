import { Card, Text, Group, Badge, Stack, Alert, Divider } from "@mantine/core";
import { IconTrendingUp, IconTrendingDown } from "@tabler/icons-react";
import type { BusinessContext, SessionIntent } from "../../../../services/sessionReplay/mockSessionDetail";
import {
  HEADERS,
  LABELS,
  STATUS_LABELS,
  MESSAGES,
} from "../../constants/strings";

interface ConversionStatusProps {
  businessContext: BusinessContext;
  sessionIntent?: SessionIntent;
}

export function ConversionStatus({
  businessContext,
  sessionIntent,
}: ConversionStatusProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.CONVERSION_STATUS}
        </Text>
        <Badge
          size="lg"
          color={sessionIntent?.completed ? "teal" : "red"}
          leftSection={
            sessionIntent?.completed ? (
              <IconTrendingUp size={14} />
            ) : (
              <IconTrendingDown size={14} />
            )
          }
        >
          {sessionIntent?.completed
            ? STATUS_LABELS.COMPLETED
            : STATUS_LABELS.ABANDONED}
        </Badge>
      </Group>

      {businessContext.isConversionSession && (
        <Stack gap="sm">
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.GOAL}
            </Text>
            <Text size="sm" fw={600}>
              {businessContext.conversionGoal}
            </Text>
          </Group>

          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.STAGE}
            </Text>
            <Badge color="violet" variant="light">
              {businessContext.conversionStage}
            </Badge>
          </Group>

          {businessContext.funnelStep && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">
                {LABELS.FUNNEL_PROGRESS}
              </Text>
              <Text size="sm" fw={500}>
                {businessContext.funnelStep}
              </Text>
            </Group>
          )}

          {businessContext.conversionValue && (
            <>
              <Divider />
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  {LABELS.TRANSACTION_VALUE}
                </Text>
                <Text
                  size="lg"
                  fw={700}
                  c={sessionIntent?.completed ? "teal" : "red"}
                >
                  ${businessContext.conversionValue.toFixed(2)}
                </Text>
              </Group>
            </>
          )}

          {sessionIntent?.abandonedAt && (
            <Alert color="red" mt="sm">
              <Text size="sm">
                {MESSAGES.ABANDONED_AT}
                <strong>{sessionIntent.abandonedAt}</strong>
              </Text>
            </Alert>
          )}
        </Stack>
      )}
    </Card>
  );
}
