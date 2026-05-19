import { Alert, Paper } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useAnalytics } from "../../hooks/useAnalytics";
import { useProjectContext } from "../../contexts";
import { useSessionReplayFilters } from "../../contexts/SessionReplayFilterContext";
import type {
  FilterGroup,
  FilterCategory,
} from "../../services/sessionReplay/filterConfig";
import {
  SESSION_LIST_LABELS,
  DEFAULT_DATE_PRESET,
} from "./constants/sessionList.constants";
import { useSessionsFilters } from "./hooks/useSessionsFilters";
import { useSessionListData } from "./hooks/useSessionListData";
import { getInteractionFilterFieldFromConfig } from "./utils/getInteractionFilterField";
import type { SortField } from "../../services/sessionReplay";
import classes from "./SessionReplaySessions.module.css";
import { SessionListHeader } from "./components/SessionListHeader";
import { SessionListLoadingState } from "./components/SessionListLoadingState";
import { SessionListEmptyState } from "./components/SessionListEmptyState";
import { AdvancedFilterBuilder } from "./components/AdvancedFilterBuilder";
import { SessionsTableToolbar } from "./components/SessionsTableToolbar";
import { SessionsVirtualList } from "./components/SessionsVirtualList";
import { trackPulseEvent } from "../../pulse-web-rum/pulseRumAnalytics";

export function SessionReplaySessions() {
  const { trackClick } = useAnalytics("SessionReplaySessions");
  const navigate = useNavigate();
  const { projectId } = useProjectContext();
  const { state: filterState, actions: filterActions } =
    useSessionReplayFilters();
  const { config: filtersConfig, loading: quickFiltersLoading } =
    useSessionsFilters();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [sortBy, setSortBy] = useState<SortField>("START_TIME");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("DESC");

  const { sessions, isLoading, isFetching, hasMore, error, loadMore } =
    useSessionListData({
      filterState,
      sortBy,
      sortDirection,
    });

  const interactionField = getInteractionFilterFieldFromConfig(filtersConfig);
  const hasSyncedInteractionDrillDownRef = useRef(false);

  useEffect(() => {
    if (
      hasSyncedInteractionDrillDownRef.current ||
      !interactionField ||
      filterState.drillDown.type !== "interaction" ||
      !filterState.drillDown.value
    ) {
      return;
    }
    const interactionName =
      typeof filterState.drillDown.value === "string"
        ? filterState.drillDown.value
        : null;
    if (!interactionName) return;
    const hasInteractionCondition =
      filterState.advancedFilters?.conditions?.some(
        (c) => c.field === interactionField.fieldKey,
      );
    if (!hasInteractionCondition) {
      hasSyncedInteractionDrillDownRef.current = true;
      filterActions.setAdvancedFilters({
        id: `critical-interaction-${interactionName}`,
        operator: "AND",
        conditions: [
          {
            id: `ci-name-${interactionName}`,
            category: interactionField.categoryKey as FilterCategory,
            field: interactionField.fieldKey,
            operator: "equals",
            value: interactionName,
          },
        ],
      });
    }
  }, [
    interactionField?.fieldKey,
    interactionField?.categoryKey,
    filterState.drillDown.type,
    filterState.drillDown.value,
    filterState.advancedFilters?.conditions,
    filterActions,
    interactionField,
  ]);

  const handleSort = (field: SortField) => {
    if (sortBy === field) {
      setSortDirection((d) => (d === "ASC" ? "DESC" : "ASC"));
    } else {
      setSortBy(field);
      setSortDirection("DESC");
    }
  };

  const toggleQuickFilter = (filterKey: string) => {
    filterActions.setQuickFilters({
      ...filterState.quickFilters,
      [filterKey]: !filterState.quickFilters[filterKey],
    });
  };

  const clearAllFilters = () => {
    filterActions.resetAll();
  };

  const removeLastFilter = () => {
    const af = filterState.advancedFilters;
    if (af && af.conditions?.length > 0) {
      const updated = af.conditions.slice(0, -1);
      filterActions.setAdvancedFilters(
        updated.length === 0 ? null : { ...af, conditions: updated },
      );
      return;
    }
    if (filterState.searchQuery) {
      filterActions.setSearchQuery("");
      return;
    }
    const activeQuickKeys = Object.entries(filterState.quickFilters)
      .filter(([, v]) => v)
      .map(([k]) => k);
    if (activeQuickKeys.length > 0) {
      filterActions.setQuickFilters({
        ...filterState.quickFilters,
        [activeQuickKeys[activeQuickKeys.length - 1]]: false,
      });
      return;
    }
    if (isNonDefaultDateRange) {
      filterActions.setDateRange(DEFAULT_DATE_PRESET);
      return;
    }
    if (filterState.drillDown.type) {
      filterActions.clearDrillDown();
    }
  };

  const handleApplyAdvancedFilters = (filterGroup: FilterGroup) => {
    const isEmpty =
      !filterGroup.conditions?.length || filterGroup.conditions.length === 0;
    filterActions.setAdvancedFilters(isEmpty ? null : filterGroup);

    const hasInteractionCondition =
      interactionField &&
      filterGroup.conditions?.some(
        (c) => c.field === interactionField.fieldKey,
      );
    if (
      filterState.drillDown.type === "interaction" &&
      !hasInteractionCondition
    ) {
      filterActions.clearDrillDown();
    }
  };

  const removeAdvancedFilter = (conditionId: string) => {
    const af = filterState.advancedFilters;
    if (!af || !Array.isArray(af.conditions)) return;
    const removedCondition = af.conditions.find((c) => c.id === conditionId);
    const updated = af.conditions.filter(
      (c: { id: string }) => c.id !== conditionId,
    );
    if (updated.length === 0) {
      filterActions.setAdvancedFilters(null);
    } else {
      filterActions.setAdvancedFilters({ ...af, conditions: updated });
    }

    if (
      removedCondition &&
      interactionField &&
      removedCondition.field === interactionField.fieldKey
    ) {
      filterActions.clearDrillDown();
    }
  };

  const advancedConditionsLength =
    filterState.advancedFilters?.conditions?.length ?? 0;
  const isNonDefaultDateRange =
    filterState.dateRange.preset !== DEFAULT_DATE_PRESET;
  const activeFiltersCount =
    Object.values(filterState.quickFilters).filter(Boolean).length +
    (filterState.searchQuery ? 1 : 0) +
    (advancedConditionsLength > 0 ? advancedConditionsLength : 0) +
    (filterState.drillDown.type && filterState.drillDown.type !== "interaction"
      ? 1
      : 0) +
    (isNonDefaultDateRange ? 1 : 0);

  const sessionReplayBase = projectId
    ? `/projects/${projectId}/session-replay`
    : "/session-replay";

  const handleWatchSession = (sessionId: string) => {
    trackClick(`WatchSession_${sessionId}`);
    trackPulseEvent("session_replay_opened", {
      session_id: sessionId,
    });
    navigate(`${sessionReplayBase}/${sessionId}`);
  };

  const isInitialLoading = isLoading && sessions.length === 0;
  const showEmptyState = !isLoading && sessions.length === 0;

  return (
    <div className={classes.container}>
      <SessionListHeader
        subtitle={
          showEmptyState
            ? SESSION_LIST_LABELS.emptyStateSubtitleFiltered
            : undefined
        }
      />

      {filterState.drillDown.type &&
        filterState.drillDown.label &&
        filterState.drillDown.type !== "interaction" && (
          <Alert
            icon={<IconInfoCircle size={16} />}
            title={SESSION_LIST_LABELS.filteredViewTitle}
            color="teal"
            withCloseButton
            onClose={() => filterActions.clearDrillDown()}
            mb="lg"
          >
            {SESSION_LIST_LABELS.filteredViewMessage}{" "}
            <strong>{filterState.drillDown.label}</strong>
          </Alert>
        )}

      <AdvancedFilterBuilder
        opened={advancedFilterOpen}
        onClose={() => setAdvancedFilterOpen(false)}
        onApply={handleApplyAdvancedFilters}
        initialFilters={filterState.advancedFilters || undefined}
        sessionsFilterConfig={filtersConfig ?? undefined}
      />

      <Paper className={classes.tableContainer} p={0} radius="md">
        <SessionsTableToolbar
          datePreset={filterState.dateRange.preset}
          dateFrom={filterState.dateRange.from}
          dateTo={filterState.dateRange.to}
          onDatePresetChange={(preset) => {
            filterActions.setDateRange(preset);
          }}
          onDateCustomChange={(from, to) => {
            filterActions.setDateRange("custom", from, to);
          }}
          sessionCount={sessions.length}
          hasMore={hasMore}
          filtersConfig={filtersConfig}
          quickFiltersLoading={quickFiltersLoading}
          quickFiltersState={
            filterState.quickFilters as Record<string, boolean>
          }
          onToggleQuickFilter={toggleQuickFilter}
          onOpenAdvancedFilters={() => setAdvancedFilterOpen(true)}
          activeFiltersCount={activeFiltersCount}
          onClearAllFilters={clearAllFilters}
          searchQuery={filterState.searchQuery}
          onSearchChange={filterActions.setSearchQuery}
          advancedOperator={filterState.advancedFilters?.operator ?? "AND"}
          advancedConditions={filterState.advancedFilters?.conditions ?? []}
          onRemoveAdvancedFilter={removeAdvancedFilter}
        />

        {isInitialLoading ? (
          <SessionListLoadingState embedded />
        ) : showEmptyState ? (
          <SessionListEmptyState
            hasActiveFilters={activeFiltersCount > 0}
            onClearFilters={clearAllFilters}
            onRemoveLastFilter={removeLastFilter}
          />
        ) : (
          <SessionsVirtualList
            sessions={sessions}
            sortBy={sortBy}
            sortDirection={sortDirection}
            onSort={handleSort}
            onSessionClick={handleWatchSession}
            onLoadMore={loadMore}
            isLoading={isLoading}
            isFetching={isFetching}
            hasMore={hasMore}
            error={error}
          />
        )}
      </Paper>
    </div>
  );
}
