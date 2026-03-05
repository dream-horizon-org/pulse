// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useQuery } from "@tanstack/react-query";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { AiSessionDetail } from "./useGetAiSessionHistory.interface";

export const useGetAiSessionHistory = (sessionId: string | null) => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  return useQuery<AiSessionDetail>({
    queryKey: ["ai-session-history", userId, sessionId],
    queryFn: async () => {
      const url = `${AI_BASE_URL}/sessions/${encodeURIComponent(userId)}/${sessionId}`;
      const response = await fetch(url);
      if (!response.ok)
        throw new Error(`Failed to fetch session: ${response.status}`);
      return response.json();
    },
    enabled: !!sessionId,
    refetchOnWindowFocus: false,
  });
};
