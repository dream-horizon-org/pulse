import { Stack, Loader, Text } from "@mantine/core";
import { ADVANCED_FILTER_LABELS } from "./constants";

export function SchemaLoadingState() {
  return (
    <Stack align="center" justify="center" py="xl">
      <Loader size="lg" />
      <Text size="sm" c="dimmed">
        {ADVANCED_FILTER_LABELS.loadingMessage}
      </Text>
    </Stack>
  );
}
