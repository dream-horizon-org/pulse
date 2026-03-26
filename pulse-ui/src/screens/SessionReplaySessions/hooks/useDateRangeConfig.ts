// Session Replay - Dynamic Date Range Configuration Hook
// Fetches date range options from API

import { useState, useEffect } from 'react';
import { sessionReplayService } from '../../../services/sessionReplay/SessionReplayService';
import { GetDateRangeConfigResponse } from '../../../services/sessionReplay/types';

interface UseDateRangeConfigResult {
  config: GetDateRangeConfigResponse | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export function useDateRangeConfig(): UseDateRangeConfigResult {
  const [config, setConfig] = useState<GetDateRangeConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchConfig = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await sessionReplayService.getDateRangeConfig();
      setConfig(response);
    } catch (err) {
      setError(err as Error);
      console.error('Failed to fetch date range config:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  return {
    config,
    loading,
    error,
    refetch: fetchConfig,
  };
}
