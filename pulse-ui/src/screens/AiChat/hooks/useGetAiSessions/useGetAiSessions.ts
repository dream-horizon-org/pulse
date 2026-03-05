// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useQuery } from "@tanstack/react-query";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { AiSessionListItem } from "./useGetAiSessions.interface";

export const useGetAiSessions = () => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  return useQuery<AiSessionListItem[]>({
    queryKey: ["ai-sessions", userId],
    queryFn: async () => {
      const url = `${AI_BASE_URL}/sessions/${encodeURIComponent(userId)}`;
      const response = await fetch(url);
      if (!response.ok)
        throw new Error(`Failed to fetch sessions: ${response.status}`);
      return response.json();
    },
    refetchOnWindowFocus: false,
    staleTime: 30_000,
  });
};
