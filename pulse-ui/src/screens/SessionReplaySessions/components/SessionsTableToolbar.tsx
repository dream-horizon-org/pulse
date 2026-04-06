import {
  Badge,
  Button,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
  Box,
  ActionIcon,
} from "@mantine/core";
import { IconSearch, IconSettings, IconTag, IconX } from "@tabler/icons-react";
import type { FilterConfigResponse } from "../../../services/sessionReplay/types";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import { TimeRangeFilter } from "./TimeRangeFilter";
import { ActiveFilterChips } from "./ActiveFilterChips";

export interface SessionsTableToolbarProps {
  // Time range
  datePreset: string;
  dateFrom: string | null;
  dateTo: string | null;
  onDatePresetChange: (preset: string) => void;
  onDateCustomChange: (from: string | null, to: string | null) => void;
  onPageReset: () => void;
  // Table summary
  sessionCount: number;
  hasMore: boolean;
  // Quick filters
  filtersConfig: FilterConfigResponse | null;
  quickFiltersLoading: boolean;
  quickFiltersState: Record<string, boolean>;
  onToggleQuickFilter: (key: string) => void;
  onOpenAdvancedFilters: () => void;
  // Active filters
  activeFiltersCount: number;
  onClearAllFilters: () => void;
  // Search
  searchQuery: string;
  onSearchChange: (value: string) => void;
  // Advanced filter chips
  advancedOperator: string;
  advancedConditions: Array<{
    id: string;
    field: string;
    operator: string;
    value?: unknown;
  }>;
  onRemoveAdvancedFilter: (id: string) => void;
}

export function SessionsTableToolbar({
  datePreset,
  dateFrom,
  dateTo,
  onDatePresetChange,
  onDateCustomChange,
  onPageReset,
  sessionCount,
  hasMore,
  filtersConfig,
  quickFiltersLoading,
  quickFiltersState,
  onToggleQuickFilter,
  onOpenAdvancedFilters,
  activeFiltersCount,
  onClearAllFilters,
  searchQuery,
  onSearchChange,
  advancedOperator,
  advancedConditions,
  onRemoveAdvancedFilter,
}: SessionsTableToolbarProps) {
  return (
    <Box
      p="md"
      style={{
        background: "var(--mantine-color-gray-0)",
        borderBottom: "1px solid var(--mantine-color-gray-2)",
      }}
    >
      <Stack gap="md">
        <TimeRangeFilter
          preset={datePreset}
          from={dateFrom}
          to={dateTo}
          onPresetChange={onDatePresetChange}
          onCustomRangeChange={onDateCustomChange}
          onPageReset={onPageReset}
        />

        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Group
            gap="xs"
            style={{ flexWrap: "wrap", flex: 1 }}
            align="flex-start"
          >
            <Text size="sm" fw={500} c="dimmed">
              {SESSION_LIST_LABELS.quickFiltersLabel}
            </Text>
            {quickFiltersLoading ? (
              <Loader size="sm" />
            ) : (
              filtersConfig?.quick?.map((filter) => {
                const isActive = quickFiltersState[filter.key] === true;
                return (
                  <Badge
                    key={filter.key}
                    variant={isActive ? "filled" : "light"}
                    color="teal"
                    style={{ cursor: "pointer" }}
                    onClick={() => onToggleQuickFilter(filter.key)}
                    leftSection={<IconTag size={12} />}
                  >
                    {filter.displayName}
                  </Badge>
                );
              })
            )}
            <Button
              variant="subtle"
              color="teal"
              size="xs"
              leftSection={<IconSettings size={14} />}
              onClick={onOpenAdvancedFilters}
            >
              {SESSION_LIST_LABELS.advancedFilters}
            </Button>
            {activeFiltersCount > 0 && (
              <>
                <Badge variant="filled" color="gray" size="sm">
                  {activeFiltersCount} {SESSION_LIST_LABELS.activeFiltersCount}
                </Badge>
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  size="sm"
                  onClick={onClearAllFilters}
                >
                  <IconX size={14} />
                </ActionIcon>
              </>
            )}
          </Group>
          <Stack gap="xs" align="flex-end" style={{ flexShrink: 0 }}>
           
            <TextInput
              leftSection={<IconSearch size={16} />}
              placeholder={SESSION_LIST_LABELS.searchPlaceholder}
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              style={{ minWidth: 300, maxWidth: 400 }}
            />
          </Stack>
        </Group>

        <ActiveFilterChips
          operator={advancedOperator}
          conditions={advancedConditions}
          filtersConfig={filtersConfig}
          onRemove={onRemoveAdvancedFilter}
        />
      </Stack>
    </Box>
  );
}
