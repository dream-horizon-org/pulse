import { Card, Text, Group, Stack, Badge, Alert } from "@mantine/core";
import { IconFlask } from "@tabler/icons-react";
import type { BusinessContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, MESSAGES } from "../../constants/strings";

interface ABTestsProps {
  businessContext: BusinessContext;
}

export function ABTests({ businessContext }: ABTestsProps) {
  if (!businessContext.experiments || businessContext.experiments.length === 0) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Group mb="md">
        <IconFlask size={18} />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.AB_TEST_ASSIGNMENT}
        </Text>
      </Group>

      <Stack gap="sm">
        {businessContext.experiments.map((exp, idx) => (
          <Group key={idx} justify="space-between">
            <Text size="sm">{exp.name}</Text>
            <Badge color="violet" variant="filled">
              {exp.variant}
            </Badge>
          </Group>
        ))}
      </Stack>

      <Alert color="violet" mt="md" icon={<IconFlask size={16} />}>
        <Text size="sm">
          This user was in <strong>{businessContext.experiments[0].variant}</strong>.
          Compare performance against control group.
        </Text>
      </Alert>
    </Card>
  );
}
