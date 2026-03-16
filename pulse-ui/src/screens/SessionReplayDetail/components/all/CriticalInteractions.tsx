import { Box, Text, Group, Badge, Card, Stack } from "@mantine/core";
import { IconCheck, IconX } from "@tabler/icons-react";
import type { CriticalInteraction } from "../../../../services/sessionReplay/mockSessionDetail";
import {
  HEADERS,
  STATUS_LABELS,
  FORMAT_STRINGS,
} from "../../constants/strings";

interface CriticalInteractionsProps {
  criticalInteractions: CriticalInteraction[];
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
}

function getStatusIcon(status: "success" | "failed" | "not_attempted") {
  if (status === "success")
    return <IconCheck size={14} color="var(--mantine-color-teal-6)" />;
  if (status === "failed")
    return <IconX size={14} color="var(--mantine-color-red-6)" />;
  return null;
}

function getStatusColor(status: "success" | "failed" | "not_attempted") {
  if (status === "success") return "teal";
  if (status === "failed") return "red";
  return "gray";
}

export function CriticalInteractions({
  criticalInteractions,
  onCriticalInteractionClick,
}: CriticalInteractionsProps) {
  const successCount = criticalInteractions.filter(
    (i) => i.status === "success",
  ).length;

  return (
    <Box>
      <Group justify="space-between" mb="xs">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.CRITICAL_INTERACTIONS}
        </Text>
        <Badge size="xs" variant="light" color="blue">
          {FORMAT_STRINGS.SUCCESSFUL_COUNT.replace(
            "{success}",
            successCount.toString(),
          ).replace("{total}", criticalInteractions.length.toString())}
        </Badge>
      </Group>
      <Card padding="sm" withBorder>
        <Stack gap="sm">
          {criticalInteractions.map((interaction) => {
            const handleClick = () => {
              if (
                interaction.timestamp !== undefined &&
                interaction.latency !== undefined &&
                onCriticalInteractionClick
              ) {
                const t0 = interaction.timestamp;
                const t1 = interaction.timestamp + interaction.latency;
                onCriticalInteractionClick(t0, t1);
              }
            };

            const apdexValue =
              interaction.apdexScore !== undefined
                ? interaction.apdexScore.toFixed(2)
                : "—";

            return (
              <Group
                key={interaction.interactionId}
                justify="space-between"
                wrap="nowrap"
                style={{
                  cursor:
                    interaction.timestamp !== undefined &&
                    interaction.latency !== undefined
                      ? "pointer"
                      : "default",
                }}
                onClick={handleClick}
              >
                <Group gap="xs" wrap="nowrap">
                  {getStatusIcon(interaction.status)}
                  <Text size="sm" fw={500} style={{ flex: 1 }}>
                    {interaction.displayName}
                  </Text>
                </Group>
                <Group gap="xs" wrap="nowrap">
                  <Text size="xs" c="dimmed">
                    Apdex {apdexValue}
                  </Text>
                  <Badge
                    size="sm"
                    color={getStatusColor(interaction.status)}
                    variant="light"
                  >
                    {interaction.status === "success"
                      ? STATUS_LABELS.SUCCESS
                      : interaction.status === "failed"
                        ? STATUS_LABELS.FAILED
                        : STATUS_LABELS.NOT_ATTEMPTED}
                  </Badge>
                </Group>
              </Group>
            );
          })}
        </Stack>
      </Card>
    </Box>
  );
}
