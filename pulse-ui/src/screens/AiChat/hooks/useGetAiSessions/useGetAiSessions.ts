import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { makeRequest } from "../../../../helpers/makeRequest";
import { AiSessionListItem } from "./useGetAiSessions.interface";
import { AI_API_PATHS, AI_CHAT_LIMITS } from "../../AiChat.constants";

/** Fetches AI session list for a user (used by TanStack Query and tests). */
async function fetchAiSessions(
  userId: string,
  signal: AbortSignal | undefined,
): Promise<AiSessionListItem[]> {
  const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}/${encodeURIComponent(userId)}`;
  const result = await makeRequest<AiSessionListItem[]>({
    url,
    init: { method: "GET", signal },
    unwrapped: true,
  });
  const hasError = result.error != null;
  if (hasError) {
    const message =
      result.error?.message ?? `Failed to fetch sessions: ${result.status}`;
    throw new Error(message);
  }
  const sessions = result.data;
  const sessionsMissing = sessions == null;
  if (sessionsMissing) {
    throw new Error(`Failed to fetch sessions: ${result.status}`);
  }
  return sessions;
}

export const useGetAiSessions = () => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  const query = useQuery<AiSessionListItem[]>({
    queryKey: ["ai-sessions", userId],
    queryFn: ({ signal }) => fetchAiSessions(userId, signal),
    // Avoid refetch on every focus; list is updated after create via queryClient.setQueryData.
    staleTime: AI_CHAT_LIMITS.SESSIONS_STALE_TIME_MS,
    refetchOnWindowFocus: false,
  });

  return { ...query, refetchSessions: query.refetch };
};
