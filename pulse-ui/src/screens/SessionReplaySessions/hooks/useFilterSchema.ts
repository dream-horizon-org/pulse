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

interface UseFilterSchemaOptions {
  projectId?: string;
  skip?: boolean;
}

export function useFilterSchema(
  projectIdOrOptions?: string | UseFilterSchemaOptions,
): UseFilterSchemaResult {
  const options: UseFilterSchemaOptions =
    typeof projectIdOrOptions === "string"
      ? { projectId: projectIdOrOptions }
      : projectIdOrOptions ?? {};
  const { projectId, skip = false } = options;

  const [schema, setSchema] = useState<GetFilterSchemaResponse | null>(null);
  const [loading, setLoading] = useState(!skip);
  const [error, setError] = useState<Error | null>(null);

  const fetchSchema = async () => {
    if (skip) return;
    try {
      setLoading(true);
      setError(null);
      const response = await sessionReplayService.getFilterSchema({ projectId });
      setSchema(response);
    } catch (err) {
      setError(err as Error);
      console.error("Failed to fetch filter schema:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (skip) {
      setLoading(false);
      return;
    }
    fetchSchema();
  }, [projectId, skip]);

  return {
    schema,
    loading,
    error,
    refetch: fetchSchema,
  };
}
