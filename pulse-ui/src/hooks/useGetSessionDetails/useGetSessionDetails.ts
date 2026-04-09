import { useQuery } from "@tanstack/react-query";
import { sessionReplayService } from "../../services/sessionReplay/SessionReplayService";
import type { SessionDetailApiResponse } from "../../services/sessionReplay/types";

/**
 * Fetches session details for a given session ID
 * Uses existing SessionReplayService.getSessionDetail()
 */
export function useGetSessionDetail(sessionId: string) {
  return useQuery<SessionDetailApiResponse>({
    queryKey: ["sessionDetail", sessionId],
    queryFn: async () => {
      const response = await sessionReplayService.getSessionDetail({
        sessionId,
      });
      return response;
    },
    enabled: !!sessionId,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: 1,
  });
}
