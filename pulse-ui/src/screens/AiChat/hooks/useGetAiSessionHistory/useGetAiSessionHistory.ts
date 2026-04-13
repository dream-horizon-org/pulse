import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { makeRequest } from "../../../../helpers/makeRequest";
import { AiSessionDetail } from "./useGetAiSessionHistory.interface";
import { AI_API_PATHS, AI_CHAT_LIMITS } from "../../AiChat.constants";

/** Fetches one AI session with message history (used by TanStack Query and tests). */
async function fetchAiSessionHistory(
  userId: string,
  sessionId: string,
  signal: AbortSignal | undefined,
): Promise<AiSessionDetail> {
  const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}/${encodeURIComponent(userId)}/${encodeURIComponent(sessionId)}`;
  const result = await makeRequest<AiSessionDetail>({
    url,
    init: { method: "GET", signal },
    unwrapped: true,
  });
  const hasError = result.error != null;
  if (hasError) {
    const message =
      result.error?.message ?? `Failed to fetch session: ${result.status}`;
    throw new Error(message);
  }
  const detail = result.data;
  const detailMissing = detail == null;
  if (detailMissing) {
    throw new Error(`Failed to fetch session: ${result.status}`);
  }
  return detail;
}

export const useGetAiSessionHistory = (sessionId: string | null) => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  return useQuery<AiSessionDetail>({
    queryKey: ["ai-session-history", userId, sessionId],
    queryFn: ({ signal }) => {
      if (!sessionId) {
        throw new Error("fetchAiSessionHistory requires sessionId");
      }
      return fetchAiSessionHistory(userId, sessionId, signal);
    },
    enabled: !!sessionId,
    staleTime: AI_CHAT_LIMITS.SESSIONS_STALE_TIME_MS,
    refetchOnWindowFocus: false,
  });
};
