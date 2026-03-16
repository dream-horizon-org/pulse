// Session Replay - Dynamic Filter Schema Hook
// Fetches filter configuration from API based on platform

import { useState, useEffect } from 'react';
import { sessionReplayService } from '../../../services/sessionReplay/SessionReplayService';
import { GetFilterSchemaResponse } from '../../../services/sessionReplay/types';

interface UseFilterSchemaResult {
  schema: GetFilterSchemaResponse | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export function useFilterSchema(projectId?: string): UseFilterSchemaResult {
  const [schema, setSchema] = useState<GetFilterSchemaResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchSchema = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await sessionReplayService.getFilterSchema({ projectId });
      setSchema(response);
    } catch (err) {
      setError(err as Error);
      console.error('Failed to fetch filter schema:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSchema();
  }, [projectId]);

  return {
    schema,
    loading,
    error,
    refetch: fetchSchema,
  };
}
