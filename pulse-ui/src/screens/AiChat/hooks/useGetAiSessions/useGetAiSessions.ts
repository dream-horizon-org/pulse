import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { makeRequestToServer } from "../../../../helpers/makeRequestToServer";
import { AiSessionListItem } from "./useGetAiSessions.interface";
import { AI_API_PATHS, AI_CHAT_LIMITS } from "../../AiChat.constants";

export const useGetAiSessions = () => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  const query = useQuery<AiSessionListItem[]>({
    queryKey: ["ai-sessions", userId],
    queryFn: async ({ signal }) => {
      const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}/${encodeURIComponent(userId)}`;
      const response = await makeRequestToServer({
        url,
        init: { method: "GET", signal },
        unwrapped: true,
      });
      if (!response.ok)
        throw new Error(`Failed to fetch sessions: ${response.status}`);
      return response.json();
    },
    staleTime: AI_CHAT_LIMITS.SESSIONS_STALE_TIME_MS,
    refetchOnWindowFocus: false,
  });

  return { ...query, refetchSessions: query.refetch };
};
