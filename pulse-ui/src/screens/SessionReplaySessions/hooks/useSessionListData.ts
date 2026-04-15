import { useState, useEffect, useCallback, useRef } from "react";
import { sessionReplayService } from "../../../services/sessionReplay/SessionReplayService";
import type {
  TimeRange,
  AdvancedFilterGroup,
  SessionItem,
} from "../../../services/sessionReplay/types";

import type { SortField, SortDirection } from "../../../services/sessionReplay";
import {
  DEFAULT_DATE_PRESET,
  SEARCH_DEBOUNCE_MS,
} from "../constants/sessionList.constants";
import {
  SessionReplayFilterState,
  INFINITE_SCROLL_PAGE_SIZE,
} from "../../../contexts/SessionReplayFilterContext";

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
  sortBy: SortField;
  sortDirection: SortDirection;
}

export interface UseSessionListDataResult {
  sessions: SessionItem[];
  cursor: string | null;
  hasMore: boolean;
  isLoading: boolean;
  isFetching: boolean;
  error: Error | null;
  loadMore: () => Promise<void>;
  refetch: () => Promise<void>;
}

export function useSessionListData({
  filterState,
  sortBy,
  sortDirection,
}: UseSessionListDataParams): UseSessionListDataResult {
  const [sessions, setSessions] = useState<SessionItem[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [isFetching, setIsFetching] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const lastFetchKeyRef = useRef("");
  const prevSearchRef = useRef(filterState.searchQuery);
  const cursorRef = useRef<string | null>(null);
  const hasMoreRef = useRef(true);
  const isFetchingRef = useRef(false);
  const isLoadingRef = useRef(true);
  const loadMoreBusyRef = useRef(false);

  // Sync refs during render so callbacks/observers never read one frame behind state.
  cursorRef.current = cursor;
  hasMoreRef.current = hasMore;
  isFetchingRef.current = isFetching;
  isLoadingRef.current = isLoading;

  const performFetch = useCallback(
    async (fetchCursor: string | null, isInitialLoad: boolean) => {
      if (isInitialLoad) {
        setIsLoading(true);
      } else {
        setIsFetching(true);
      }

      try {
        const timeRange = buildTimeRangeFromState(filterState);
        const quick = Object.entries(filterState.quickFilters)
          .filter(([, v]) => v)
          .map(([k]) => k);
        const advanced = buildAdvancedGroupFromState(filterState);

        const response = await sessionReplayService.postSessionsListing({
          timeRange,
          page: {
            limit: INFINITE_SCROLL_PAGE_SIZE,
            cursor: fetchCursor ?? undefined,
          },
          filters:
            quick.length > 0 || advanced
              ? { quick: quick.length ? quick : undefined, advanced }
              : undefined,
          query: filterState.searchQuery || undefined,
          sortBy,
          sortDirection,
        });

        setError(null);

        if (isInitialLoad) {
          setSessions(response.sessions);
        } else {
          setSessions((prev) => [...prev, ...response.sessions]);
        }

        const next = response.page.nextCursor ?? null;
        setCursor(next);
        // Drive UI pagination state from the API hasMore flag. nextCursor is still
        // required for loadMore(); if it is missing while hasMore is true, loadMore
        // no-ops until the contract is fixed (backend always sends both when hasMore).
        setHasMore(response.page.hasMore === true);
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err));
        setError(error);
        console.error("Failed to fetch sessions:", error);
      } finally {
        if (isInitialLoad) {
          setIsLoading(false);
        } else {
          setIsFetching(false);
        }
      }
    },
    [filterState, sortBy, sortDirection],
  );

  const refetch = useCallback(async () => {
    setSessions([]);
    setCursor(null);
    setHasMore(true);
    setError(null);
    await performFetch(null, true);
  }, [performFetch]);

  const loadMore = useCallback(async () => {
    if (loadMoreBusyRef.current) {
      return;
    }
    loadMoreBusyRef.current = true;
    try {
      if (
        !hasMoreRef.current ||
        isFetchingRef.current ||
        isLoadingRef.current
      ) {
        return;
      }

      if (cursorRef.current === null) {
        return;
      }

      await performFetch(cursorRef.current, false);
    } finally {
      loadMoreBusyRef.current = false;
    }
  }, [performFetch]);

  const isCustomWithoutDates =
    filterState.dateRange.preset === "custom" &&
    !filterState.dateRange.from &&
    !filterState.dateRange.to;

  useEffect(() => {
    if (isCustomWithoutDates) return;

    const fetchKey = JSON.stringify([
      filterState.dateRange,
      filterState.quickFilters,
      filterState.advancedFilters,
      filterState.drillDown,
      sortBy,
      sortDirection,
    ]);

    if (fetchKey === lastFetchKeyRef.current) return;
    lastFetchKeyRef.current = fetchKey;

    refetch();
  }, [
    filterState.dateRange,
    filterState.quickFilters,
    filterState.advancedFilters,
    filterState.drillDown,
    sortBy,
    sortDirection,
    isCustomWithoutDates,
    refetch,
  ]);

  useEffect(() => {
    if (prevSearchRef.current === filterState.searchQuery) return;
    prevSearchRef.current = filterState.searchQuery;

    const timer = setTimeout(() => {
      lastFetchKeyRef.current = "";
      refetch();
    }, SEARCH_DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [filterState.searchQuery, refetch]);

  return {
    sessions,
    cursor,
    hasMore,
    isLoading,
    isFetching,
    error,
    loadMore,
    refetch,
  };
}
