import {
  Card,
  Text,
  Group,
  Badge,
  Timeline,
  Button,
  CopyButton,
} from "@mantine/core";
import { IconChecklist, IconCheck, IconCopy } from "@tabler/icons-react";
import {
  HEADERS,
  LABELS,
  BUTTON_LABELS,
  FORMAT_STRINGS,
  MESSAGES_EXTENDED as MESSAGES,
} from "../../constants/strings";

interface ReproducibilityProps {
  reproducibilityScore: number;
  reproductionSteps?: string[];
}

export function Reproducibility({
  reproducibilityScore,
  reproductionSteps,
}: ReproducibilityProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Group gap="xs">
          <IconChecklist size={18} />
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">
            {HEADERS.REPRODUCIBILITY}
          </Text>
        </Group>
        <Badge
          size="lg"
          color={
            reproducibilityScore >= 80
              ? "teal"
              : reproducibilityScore >= 50
                ? "yellow"
                : "red"
          }
        >
          {FORMAT_STRINGS.REPRODUCIBILITY_SCORE.replace(
            "{score}",
            reproducibilityScore.toString(),
          )}
        </Badge>
      </Group>

      {reproductionSteps && reproductionSteps.length > 0 && (
        <>
          <Text size="sm" fw={500} mb="sm">
            {LABELS.REPRODUCTION_STEPS}:
          </Text>
          <Timeline bulletSize={20} lineWidth={2}>
            {reproductionSteps.map((step, idx) => (
              <Timeline.Item
                key={idx}
                bullet={<Text size="xs">{idx + 1}</Text>}
                color="teal"
              >
                <Text size="sm">{step}</Text>
              </Timeline.Item>
            ))}
          </Timeline>

          <CopyButton value={reproductionSteps.join("\n")}>
            {({ copied, copy }) => (
              <Button
                variant="light"
                fullWidth
                mt="md"
                onClick={copy}
                leftSection={
                  copied ? <IconCheck size={16} /> : <IconCopy size={16} />
                }
              >
                {copied
                  ? MESSAGES.COPIED_EXCLAMATION
                  : BUTTON_LABELS.COPY_REPRO_STEPS}
              </Button>
            )}
          </CopyButton>
        </>
      )}
    </Card>
  );
}
