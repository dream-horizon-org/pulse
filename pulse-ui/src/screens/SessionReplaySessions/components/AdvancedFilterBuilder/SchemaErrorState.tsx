import { Paper, Text } from "@mantine/core";
import { ADVANCED_FILTER_LABELS, MODAL_STYLES } from "./constants";

export function SchemaErrorState() {
  return (
    <Paper
      p="xl"
      withBorder
      style={{ backgroundColor: MODAL_STYLES.errorStateBg }}
    >
      <Text size="sm" c="red" ta="center">
        {ADVANCED_FILTER_LABELS.errorMessage}
      </Text>
    </Paper>
  );
}
