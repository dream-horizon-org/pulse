// Session Replay - Dynamic Quick Filters Hook
// Fetches quick filter configuration from API

import { useState, useEffect } from 'react';
import { sessionReplayService } from '../../../services/sessionReplay/SessionReplayService';
import { GetQuickFiltersResponse } from '../../../services/sessionReplay/types';

interface UseQuickFiltersResult {
  quickFilters: GetQuickFiltersResponse | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export function useQuickFilters(): UseQuickFiltersResult {
  const [quickFilters, setQuickFilters] = useState<GetQuickFiltersResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchQuickFilters = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await sessionReplayService.getQuickFilters();
      setQuickFilters(response);
    } catch (err) {
      setError(err as Error);
      console.error('Failed to fetch quick filters:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchQuickFilters();
  }, []);

  return {
    quickFilters,
    loading,
    error,
    refetch: fetchQuickFilters,
  };
}
