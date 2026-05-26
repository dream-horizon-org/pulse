import { useQuery } from "@tanstack/react-query";
import { sessionReplayService } from "../../../services/sessionReplay/SessionReplayService";

export const SESSION_JOURNEY_QUERY_KEY = "sessionJourney";

export interface UseSessionJourneyParams {
  sessionId: string | undefined;
  startTime?: string;
  endTime?: string;
  enabled?: boolean;
}

export interface UseSessionJourneyResult {
  journey: string[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
}

export function useSessionJourney({
  sessionId,
  startTime,
  endTime,
  enabled = true,
}: UseSessionJourneyParams): UseSessionJourneyResult {
  const effectiveEnabled = Boolean(enabled && sessionId);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [SESSION_JOURNEY_QUERY_KEY, sessionId, startTime, endTime],
    queryFn: () =>
      sessionReplayService.getSessionJourney({
        sessionId: sessionId!,
        startTime,
        endTime,
      }),
    enabled: effectiveEnabled,
    staleTime: 5 * 60_000,
  });

  return {
    journey: data?.journey ?? [],
    isLoading,
    isError,
    error: error as Error | null,
    refetch,
  };
}
