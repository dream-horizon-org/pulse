import { Card, Text, Stack, Button } from "@mantine/core";
import { IconChartLine, IconTarget } from "@tabler/icons-react";
import { HEADERS, BUTTON_LABELS } from "../../constants/strings";

export function ProductActions() {
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
        {HEADERS.PRODUCT_ACTIONS}
      </Text>

      <Stack gap="xs">
        <Button
          variant="filled"
          color="violet"
          leftSection={<IconChartLine size={16} />}
        >
          {BUTTON_LABELS.CREATE_FUNNEL_ANALYSIS}
        </Button>
        <Button variant="light" leftSection={<IconTarget size={16} />}>
          {BUTTON_LABELS.FIND_SIMILAR_DROP_OFFS}
        </Button>
        <Button variant="light">{BUTTON_LABELS.ADD_TO_WATCH_LIST}</Button>
      </Stack>
    </Card>
  );
}
