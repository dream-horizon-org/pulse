import { createContext, useContext, useState, ReactNode } from "react";
import { FilterGroup } from "../services/sessionReplay/filterConfig";

export const INFINITE_SCROLL_PAGE_SIZE = 25;

/**
 * Drill-down types for navigating from Insights to Session List
 */
export type DrillDownType =
  | "conversion_loss"
  | "affected_users"
  | "sessions_with_issues"
  | "interaction"
  | "journey"
  | "cardinality"
  | "error_pattern"
  | "friction_hotspot";

/**
 * Filter state for Session Replay
 * Shared across Insights, Session List, and Detail pages
 */
export interface SessionReplayFilterState {
  // Date range (global, affects all pages)
  dateRange: {
    preset: string;
    from: string | null;
    to: string | null;
  };

  // Drill-down context (set when navigating from Insights to Session List)
  drillDown: {
    type: DrillDownType | null;
    value: string | string[] | Record<string, any> | null;
    label: string | null;
  };

  // User-applied filters (on Session List page)
  quickFilters: Record<string, boolean>;
  advancedFilters: FilterGroup | null;
  searchQuery: string;
}

/**
 * Actions for managing filter state
 */
export interface SessionReplayFilterActions {
  setDateRange: (
    preset: string,
    from?: string | null,
    to?: string | null,
  ) => void;
  setDrillDown: (type: DrillDownType, value: any, label: string) => void;
  clearDrillDown: () => void;
  setQuickFilters: (filters: Record<string, boolean>) => void;
  setAdvancedFilters: (filters: FilterGroup | null) => void;
  setSearchQuery: (query: string) => void;
  resetFilters: () => void;
  resetAll: () => void;
}

/**
 * Context value combining state and actions
 */
export interface SessionReplayFilterContextValue {
  state: SessionReplayFilterState;
  actions: SessionReplayFilterActions;
}

const SessionReplayFilterContext = createContext<
  SessionReplayFilterContextValue | undefined
>(undefined);

const DEFAULT_STATE: SessionReplayFilterState = {
  dateRange: {
    preset: "24h",
    from: null,
    to: null,
  },
  drillDown: {
    type: null,
    value: null,
    label: null,
  },
  quickFilters: {},
  advancedFilters: null,
  searchQuery: "",
};

/**
 * Provider component for Session Replay filter state
 * Wrap this around all Session Replay pages
 */
export function SessionReplayFilterProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [state, setState] = useState<SessionReplayFilterState>(DEFAULT_STATE);

  const actions: SessionReplayFilterActions = {
    setDateRange: (preset, from = null, to = null) => {
      setState((prev) => ({
        ...prev,
        dateRange: { preset, from, to },
      }));
    },

    setDrillDown: (type, value, label) => {
      setState((prev) => ({
        ...prev,
        drillDown: { type, value, label },
      }));
    },

    clearDrillDown: () => {
      setState((prev) => ({
        ...prev,
        drillDown: { type: null, value: null, label: null },
      }));
    },

    setQuickFilters: (filters) => {
      setState((prev) => ({
        ...prev,
        quickFilters: filters,
      }));
    },

    setAdvancedFilters: (filters) => {
      setState((prev) => ({
        ...prev,
        advancedFilters: filters,
      }));
    },

    setSearchQuery: (query) => {
      setState((prev) => ({
        ...prev,
        searchQuery: query,
      }));
    },

    resetFilters: () => {
      setState((prev) => ({
        ...prev,
        quickFilters: {},
        advancedFilters: null,
        searchQuery: "",
      }));
    },

    resetAll: () => {
      setState(DEFAULT_STATE);
    },
  };

  return (
    <SessionReplayFilterContext.Provider value={{ state, actions }}>
      {children}
    </SessionReplayFilterContext.Provider>
  );
}

/**
 * Hook to access Session Replay filter context
 * Must be used within SessionReplayFilterProvider
 */
export function useSessionReplayFilters() {
  const context = useContext(SessionReplayFilterContext);
  if (!context) {
    throw new Error(
      "useSessionReplayFilters must be used within SessionReplayFilterProvider",
    );
  }
  return context;
}
