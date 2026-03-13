import { useState, useEffect, useCallback, useRef } from "react";
import { sessionReplayService } from "../../../services/sessionReplay/SessionReplayService";
import type {
  SessionListingResponse,
  TimeRange,
  AdvancedFilterGroup,
} from "../../../services/sessionReplay/types";

import type { SortField, SortDirection } from "../../../services/sessionReplay";
import {
  DEFAULT_DATE_PRESET,
  SEARCH_DEBOUNCE_MS,
} from "../constants/sessionList.constants";
import { SessionReplayFilterState } from "../../../contexts/SessionReplayFilterContext";

function buildTimeRangeFromState(
  filterState: SessionReplayFilterState,
): TimeRange {
  const now = new Date();
  const preset = String(filterState.dateRange.preset ?? DEFAULT_DATE_PRESET);

  if (preset === "custom") {
    const from = filterState.dateRange.from ?? now.toISOString();
    const to = filterState.dateRange.to ?? now.toISOString();
    return { from, to };
  }

  const hoursMatch = preset.match(/^(\d+)h$/);
  if (hoursMatch) {
    const hours = parseInt(hoursMatch[1], 10);
    const from = new Date(now.getTime() - hours * 60 * 60 * 1000);
    return { from: from.toISOString(), to: now.toISOString() };
  }

  const daysMatch = preset.match(/^(\d+)d$/);
  if (daysMatch) {
    const days = parseInt(daysMatch[1], 10);
    const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
    return { from: from.toISOString(), to: now.toISOString() };
  }

  const days = parseInt(preset, 10) || 7;
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: now.toISOString() };
}

function buildAdvancedGroupFromState(
  filterState: SessionReplayFilterState,
): AdvancedFilterGroup | undefined {
  const af = filterState.advancedFilters;
  const conditions = af?.conditions;
  if (!af || !Array.isArray(conditions) || conditions.length === 0)
    return undefined;
  return {
    op: af.operator,
    children: conditions.map((c) => ({
      field: c.field,
      operator: c.operator,
      value: c.value,
    })),
  };
}

export interface UseSessionListDataParams {
  filterState: SessionReplayFilterState;
  filterActions: { setPage: (page: number) => void };
  sortBy: SortField;
  sortDirection: SortDirection;
}

export interface UseSessionListDataResult {
  loading: boolean;
  sessionsData: SessionListingResponse | null;
  sessions: SessionListingResponse["sessions"];
  hasMorePages: boolean;
  fetchSessions: () => Promise<void>;
}

export function useSessionListData({
  filterState,
  filterActions,
  sortBy,
  sortDirection,
}: UseSessionListDataParams): UseSessionListDataResult {
  const [loading, setLoading] = useState(true);
  const [sessionsData, setSessionsData] =
    useState<SessionListingResponse | null>(null);
  const [pageCursors, setPageCursors] = useState<(string | null)[]>([]);

  const fetchSessions = useCallback(async () => {
    setLoading(true);
    try {
      const timeRange = buildTimeRangeFromState(filterState);
      const quick = Object.entries(filterState.quickFilters)
        .filter(([, v]) => v)
        .map(([k]) => k);
      const advanced = buildAdvancedGroupFromState(filterState);
      const cursor =
        filterState.currentPage === 1
          ? undefined
          : (pageCursors[filterState.currentPage - 2] ?? undefined);

      const response = await sessionReplayService.postSessionsListing({
        timeRange,
        page: {
          limit: filterState.pageSize,
          cursor: cursor ?? undefined,
        },
        filters:
          quick.length > 0 || advanced
            ? { quick: quick.length ? quick : undefined, advanced }
            : undefined,
        query: filterState.searchQuery || undefined,
        sortBy,
        sortDirection,
      });

      setSessionsData(response);
      if (response.page.nextCursor != null) {
        setPageCursors((prev) => {
          const next = [...prev];
          next[filterState.currentPage - 1] = response.page.nextCursor!;
          return next;
        });
      }
    } catch (error) {
      console.error("Failed to fetch sessions:", error);
    } finally {
      setLoading(false);
    }
  }, [
    filterState.currentPage,
    filterState.dateRange,
    filterState.quickFilters,
    filterState.advancedFilters,
    filterState.drillDown,
    filterState.pageSize,
    filterState.searchQuery,
    pageCursors,
    sortBy,
    sortDirection,
  ]);

  const fetchRef = useRef(fetchSessions);
  fetchRef.current = fetchSessions;

  useEffect(() => {
    fetchRef.current();
  }, [
    filterState.currentPage,
    filterState.dateRange,
    filterState.quickFilters,
    filterState.advancedFilters,
    filterState.drillDown,
    filterState.pageSize,
    sortBy,
    sortDirection,
  ]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (filterState.currentPage === 1) {
        fetchRef.current();
      } else {
        filterActions.setPage(1);
      }
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterState.searchQuery]);

  const sessions = sessionsData?.sessions ?? [];
  const hasMorePages = sessionsData?.page?.hasMore ?? false;

  return {
    loading,
    sessionsData,
    sessions,
    hasMorePages,
    fetchSessions,
  };
}
