/**
 * Fetches session detail per contract: GET /v1/session-replay/sessions/{sessionId}
 * with optional include=events,exceptions and adapts to SessionDetailData for the UI.
 */

import { useQuery } from "@tanstack/react-query";
import { sessionReplayService } from "../../../services/sessionReplay/SessionReplayService";
import { sessionDetailApiToData } from "../adapters/sessionDetailApiToData";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

export const SESSION_DETAIL_QUERY_KEY = "sessionDetail";

export interface UseSessionDetailParams {
  sessionId: string | undefined;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  /** Include events and exceptions for timeline/flame chart. Default true. */
  includeEvents?: boolean;
  enabled?: boolean;
}

export interface UseSessionDetailResult {
  data: SessionDetailData | undefined;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
}

export function useSessionDetail({
  sessionId,
  startTime,
  endTime,
  durationMs,
  includeEvents = true,
  enabled = true,
}: UseSessionDetailParams): UseSessionDetailResult {
  const effectiveEnabled = Boolean(enabled && sessionId);

  const {
    data: apiData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: [
      SESSION_DETAIL_QUERY_KEY,
      sessionId,
      startTime,
      endTime,
      durationMs,
    ],
    queryFn: () =>
      sessionReplayService.getSessionDetail({
        sessionId: sessionId!,
        startTime,
        endTime,
        durationMs,
        include: includeEvents
          ? (["events", "exceptions"] as const)
          : undefined,
      }),
    enabled: effectiveEnabled,
  });

  const data: SessionDetailData | undefined = apiData
    ? sessionDetailApiToData(apiData)
    : undefined;

  return {
    data,
    isLoading,
    isError,
    error: error as Error | null,
    refetch,
  };
}
