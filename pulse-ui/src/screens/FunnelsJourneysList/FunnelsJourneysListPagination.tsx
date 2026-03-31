import { useCallback, useState } from "react";
import { Button, Group, Paper, Select, Text, TextInput } from "@mantine/core";
import {
  PAGE_SIZE_OPTIONS,
  PAGINATION_NEXT,
  PAGINATION_PAGE_LABEL,
  PAGINATION_PREVIOUS,
  PAGINATION_ROWS_PER_PAGE,
  PAGINATION_SHOWING_RANGE,
} from "./FunnelsJourneysList.constants";
import classes from "./FunnelsJourneysList.module.css";

export interface FunnelsJourneysListPaginationProps {
  currentPage: number;
  totalPages: number;
  totalCount: number;
  pageSize: number;
  onPrevious: () => void;
  onNext: () => void;
  onGoToPage: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

export function FunnelsJourneysListPagination({
  currentPage,
  totalPages,
  totalCount,
  pageSize,
  onPrevious,
  onNext,
  onGoToPage,
  onPageSizeChange,
}: FunnelsJourneysListPaginationProps) {
  const [editValue, setEditValue] = useState<string | null>(null);
  const isEditing = editValue !== null;

  const from = totalCount === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, totalCount);

  const commitPage = useCallback(() => {
    if (editValue === null) return;
    const page = parseInt(editValue, 10);
    if (
      Number.isFinite(page) &&
      page >= 1 &&
      page <= totalPages &&
      page !== currentPage
    ) {
      onGoToPage(page);
    }
    setEditValue(null);
  }, [editValue, currentPage, totalPages, onGoToPage]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter") commitPage();
      if (e.key === "Escape") setEditValue(null);
    },
    [commitPage],
  );

  const hasMorePages = currentPage < totalPages;

  return (
    <Paper className={classes.paginationBar} p="md" radius="md">
      <Group justify="space-between" align="center" wrap="wrap" gap="md">
        <Group gap="md" align="center" wrap="wrap">
          <Text size="sm" c="dimmed">
            {PAGINATION_SHOWING_RANGE(from, to, totalCount)}
          </Text>
          <Group gap={6} align="center" wrap="nowrap">
            <Text size="sm" c="dimmed">
              {PAGINATION_ROWS_PER_PAGE}
            </Text>
            <Select
              size="sm"
              w={90}
              data={PAGE_SIZE_OPTIONS.map((n) => ({
                value: String(n),
                label: String(n),
              }))}
              value={String(pageSize)}
              onChange={(v) => {
                if (v) onPageSizeChange(parseInt(v, 10));
              }}
            />
          </Group>
        </Group>

        <Group gap="xs" align="center" wrap="wrap">
          <Button
            variant="light"
            color="teal"
            size="sm"
            disabled={currentPage <= 1}
            onClick={onPrevious}
          >
            {PAGINATION_PREVIOUS}
          </Button>

          <Group gap={4} align="center">
            <Text size="sm" c="dimmed">
              {PAGINATION_PAGE_LABEL}
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
                  cursor: "pointer",
                  padding: "2px 8px",
                  borderRadius: 4,
                  border: "1px solid var(--mantine-color-gray-3)",
                  minWidth: 36,
                  textAlign: "center",
                  userSelect: "none",
                }}
                onClick={() => setEditValue(String(currentPage))}
              >
                {currentPage}
              </Text>
            )}
            {totalPages > 1 && (
              <Text size="sm" c="dimmed">
                / {totalPages}
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
            {PAGINATION_NEXT}
          </Button>
        </Group>
      </Group>
    </Paper>
  );
}
