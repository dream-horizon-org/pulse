import { useState, useCallback } from "react";
import { Button, Group, Paper, Text, TextInput } from "@mantine/core";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListPaginationProps {
  currentPage: number;
  hasMorePages: boolean;
  maxPage: number;
  onPrevious: () => void;
  onNext: () => void;
  onGoToPage?: (page: number) => void;
}

export function SessionListPagination({
  currentPage,
  hasMorePages,
  maxPage,
  onPrevious,
  onNext,
  onGoToPage,
}: SessionListPaginationProps) {
  const [editValue, setEditValue] = useState<string | null>(null);
  const isEditing = editValue !== null;

  const commitPage = useCallback(() => {
    if (editValue === null) return;
    const page = parseInt(editValue, 10);
    if (
      Number.isFinite(page) &&
      page >= 1 &&
      page <= maxPage &&
      page !== currentPage
    ) {
      onGoToPage?.(page);
    }
    setEditValue(null);
  }, [editValue, currentPage, maxPage, onGoToPage]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter") commitPage();
      if (e.key === "Escape") setEditValue(null);
    },
    [commitPage],
  );

  return (
    <Paper className={classes.bottomBar} p="md" radius="md">
      <Group justify="flex-end" style={{ flexWrap: "wrap", gap: 16 }}>
        <Group gap="xs" align="center">
          <Button
            variant="light"
            color="teal"
            size="sm"
            disabled={currentPage <= 1}
            onClick={onPrevious}
          >
            {SESSION_LIST_LABELS.previous}
          </Button>

          <Group gap={4} align="center">
            <Text size="sm" c="dimmed">
              Page
            </Text>
            {isEditing ? (
              <TextInput
                size="xs"
                value={editValue}
                onChange={(e) => setEditValue(e.currentTarget.value)}
                onBlur={commitPage}
                onKeyDown={handleKeyDown}
                autoFocus
                styles={{
                  input: {
                    width: 48,
                    textAlign: "center",
                    padding: "2px 4px",
                  },
                }}
              />
            ) : (
              <Text
                size="sm"
                fw={600}
                style={{
                  cursor: onGoToPage ? "pointer" : "default",
                  padding: "2px 8px",
                  borderRadius: 4,
                  border: "1px solid var(--mantine-color-gray-3)",
                  minWidth: 36,
                  textAlign: "center",
                  userSelect: "none",
                }}
                onClick={() => onGoToPage && setEditValue(String(currentPage))}
              >
                {currentPage}
              </Text>
            )}
            {maxPage > 1 && (
              <Text size="sm" c="dimmed">
                / {maxPage}
              </Text>
            )}
          </Group>

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
