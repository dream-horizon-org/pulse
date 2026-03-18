import { useState, useEffect } from "react";
import { sessionReplayService } from "../../../services/sessionReplay/SessionReplayService";
import { FilterConfigResponse } from "../../../services/sessionReplay/types";

interface UseSessionsFiltersResult {
  config: FilterConfigResponse | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export function useSessionsFilters(): UseSessionsFiltersResult {
  const [config, setConfig] = useState<FilterConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchConfig = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await sessionReplayService.getSessionsFilters();
      setConfig(response);
    } catch (err) {
      setError(err as Error);
      console.error("Failed to fetch sessions filters:", err);
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
