import { Alert, Paper } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAnalytics } from "../../hooks/useAnalytics";
import { useSessionReplayFilters } from "../../contexts/SessionReplayFilterContext";
import { FilterGroup } from "../../services/sessionReplay/filterConfig";
import { SESSION_LIST_LABELS } from "./constants/sessionList.constants";
import { useSessionsFilters } from "./hooks/useSessionsFilters";
import { useSessionListData } from "./hooks/useSessionListData";
import type { SortField } from "../../services/sessionReplay";
import classes from "./SessionReplaySessions.module.css";
import { SessionListHeader } from "./components/SessionListHeader";
import { SessionListLoadingState } from "./components/SessionListLoadingState";
import { SessionListEmptyState } from "./components/SessionListEmptyState";
import { AdvancedFilterBuilder } from "./components/AdvancedFilterBuilder";
import { SessionsTableToolbar } from "./components/SessionsTableToolbar";
import { SessionsTable } from "./components/SessionsTable";
import { SessionListPagination } from "./components/SessionListPagination";

export function SessionReplaySessions() {
  const { trackClick } = useAnalytics("SessionReplaySessions");
  const navigate = useNavigate();
  const { state: filterState, actions: filterActions } =
    useSessionReplayFilters();
  const { config: filtersConfig, loading: quickFiltersLoading } =
    useSessionsFilters();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [sortBy, setSortBy] = useState<SortField>("START_TIME");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("DESC");

  const { loading, sessionsData, sessions, hasMorePages } = useSessionListData({
    filterState,
    filterActions,
    sortBy,
    sortDirection,
  });

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
    filterActions.resetFilters();
    filterActions.clearDrillDown();
  };

  const handleApplyAdvancedFilters = (filterGroup: FilterGroup) => {
    filterActions.setAdvancedFilters(filterGroup);
  };

  const removeAdvancedFilter = (conditionId: string) => {
    const af = filterState.advancedFilters;
    if (!af || !Array.isArray(af.conditions)) return;
    const updated = af.conditions.filter(
      (c: { id: string }) => c.id !== conditionId,
    );
    if (updated.length === 0) {
      filterActions.setAdvancedFilters(null);
    } else {
      filterActions.setAdvancedFilters({ ...af, conditions: updated });
    }
  };

  const advancedConditionsLength =
    filterState.advancedFilters?.conditions?.length ?? 0;
  const activeFiltersCount =
    Object.values(filterState.quickFilters).filter(Boolean).length +
    (filterState.searchQuery ? 1 : 0) +
    (advancedConditionsLength > 0 ? advancedConditionsLength : 0) +
    (filterState.drillDown.type ? 1 : 0);

  const handleWatchSession = (sessionId: string) => {
    trackClick(`WatchSession_${sessionId}`);
    navigate(`/session-replay/${sessionId}`);
  };

  const handleOpenInNewTab = (sessionId: string) => {
    trackClick(`OpenSession_${sessionId}`);
    window.open(`/session-replay/${sessionId}`, "_blank");
  };

  if (loading && !sessionsData) {
    return <SessionListLoadingState />;
  }

  if (!loading && sessionsData && sessions.length === 0) {
    return (
      <SessionListEmptyState
        hasActiveFilters={activeFiltersCount > 0}
        onClearFilters={clearAllFilters}
      />
    );
  }

  return (
    <div className={classes.container}>
      <SessionListHeader />

      {filterState.drillDown.type && filterState.drillDown.label && (
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
          onPageReset={() => filterActions.setPage(1)}
          sessionCount={sessions.length}
          hasMore={sessionsData?.page?.hasMore ?? false}
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

        <SessionsTable
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={handleSort}
          sessions={sessions}
          onWatchSession={handleWatchSession}
          onOpenSessionInNewTab={handleOpenInNewTab}
        />
      </Paper>

      <SessionListPagination
        currentPage={filterState.currentPage}
        hasMorePages={hasMorePages}
        onPrevious={() => filterActions.setPage(filterState.currentPage - 1)}
        onNext={() => filterActions.setPage(filterState.currentPage + 1)}
      />
    </div>
  );
}
