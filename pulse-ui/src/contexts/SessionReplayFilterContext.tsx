import { createContext, useContext, useState, ReactNode } from 'react';
import { FilterGroup } from '../services/sessionReplay/filterConfig';

/**
 * Drill-down types for navigating from Insights to Session List
 */
export type DrillDownType =
  | 'conversion_loss'
  | 'affected_users'
  | 'sessions_with_issues'
  | 'interaction'
  | 'journey'
  | 'cardinality'
  | 'error_pattern'
  | 'friction_hotspot';

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

  // Pagination
  currentPage: number;
  pageSize: number;
}

/**
 * Actions for managing filter state
 */
export interface SessionReplayFilterActions {
  setDateRange: (preset: string, from?: string | null, to?: string | null) => void;
  setDrillDown: (type: DrillDownType, value: any, label: string) => void;
  clearDrillDown: () => void;
  setQuickFilters: (filters: Record<string, boolean>) => void;
  setAdvancedFilters: (filters: FilterGroup | null) => void;
  setSearchQuery: (query: string) => void;
  setPage: (page: number) => void;
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

const SessionReplayFilterContext = createContext<SessionReplayFilterContextValue | undefined>(undefined);

const DEFAULT_STATE: SessionReplayFilterState = {
  dateRange: {
    preset: '7d',
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
  searchQuery: '',
  currentPage: 1,
  pageSize: 10,
};

/**
 * Provider component for Session Replay filter state
 * Wrap this around all Session Replay pages
 */
export function SessionReplayFilterProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionReplayFilterState>(DEFAULT_STATE);

  const actions: SessionReplayFilterActions = {
    setDateRange: (preset, from = null, to = null) => {
      setState(prev => ({
        ...prev,
        dateRange: { preset, from, to },
        currentPage: 1, // Reset pagination when date range changes
      }));
    },

    setDrillDown: (type, value, label) => {
      setState(prev => ({
        ...prev,
        drillDown: { type, value, label },
        currentPage: 1,
      }));
    },

    clearDrillDown: () => {
      setState(prev => ({
        ...prev,
        drillDown: { type: null, value: null, label: null },
        currentPage: 1,
      }));
    },

    setQuickFilters: (filters) => {
      setState(prev => ({
        ...prev,
        quickFilters: filters,
        currentPage: 1,
      }));
    },

    setAdvancedFilters: (filters) => {
      setState(prev => ({
        ...prev,
        advancedFilters: filters,
        currentPage: 1,
      }));
    },

    setSearchQuery: (query) => {
      setState(prev => ({
        ...prev,
        searchQuery: query,
        currentPage: 1,
      }));
    },

    setPage: (page) => {
      setState(prev => ({
        ...prev,
        currentPage: page,
      }));
    },

    resetFilters: () => {
      setState(prev => ({
        ...prev,
        quickFilters: {},
        advancedFilters: null,
        searchQuery: '',
        currentPage: 1,
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
    throw new Error('useSessionReplayFilters must be used within SessionReplayFilterProvider');
  }
  return context;
}
