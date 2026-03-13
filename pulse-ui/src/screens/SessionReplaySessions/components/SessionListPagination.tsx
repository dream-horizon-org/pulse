import { Button, Group, Paper, Text } from "@mantine/core";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListPaginationProps {
  currentPage: number;
  hasMorePages: boolean;
  onPrevious: () => void;
  onNext: () => void;
}

export function SessionListPagination({
  currentPage,
  hasMorePages,
  onPrevious,
  onNext,
}: SessionListPaginationProps) {
  return (
    <Paper className={classes.bottomBar} p="md" radius="md">
      <Group justify="flex-end" style={{ flexWrap: "wrap", gap: 16 }}>
        <Group gap="xs">
          <Button
            variant="light"
            color="teal"
            size="sm"
            disabled={currentPage <= 1}
            onClick={onPrevious}
          >
            {SESSION_LIST_LABELS.previous}
          </Button>
          <Text size="sm" c="dimmed">
            Page {currentPage}
          </Text>
          <Button
            variant="light"
            color="teal"
            size="sm"
            disabled={!hasMorePages}
            onClick={onNext}
          >
            {SESSION_LIST_LABELS.next}
          </Button>
        </Group>
      </Group>
    </Paper>
  );
}
