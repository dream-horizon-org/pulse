// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useQuery } from "@tanstack/react-query";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { AiSessionListItem } from "./useGetAiSessions.interface";
import { AI_API_PATHS, AI_CHAT_LIMITS } from "../../AiChat.constants";

export const useGetAiSessions = () => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  return useQuery<AiSessionListItem[]>({
    queryKey: ["ai-sessions", userId],
    queryFn: async () => {
      const url = `${AI_BASE_URL}${AI_API_PATHS.SESSIONS}/${encodeURIComponent(userId)}`;
      const response = await fetch(url);
      if (!response.ok)
        throw new Error(`Failed to fetch sessions: ${response.status}`);
      return response.json();
    },
    refetchOnWindowFocus: false,
    staleTime: AI_CHAT_LIMITS.SESSIONS_STALE_TIME_MS,
  });
};
