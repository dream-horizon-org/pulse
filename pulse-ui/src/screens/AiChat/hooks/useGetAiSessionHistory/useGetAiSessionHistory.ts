// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { buildAuthHeaders } from "../../../../helpers/makeRequestToServer";
import { AiSessionDetail } from "./useGetAiSessionHistory.interface";
import { AI_API_PATHS } from "../../AiChat.constants";

export const useGetAiSessionHistory = (sessionId: string | null) => {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  return useQuery<AiSessionDetail>({
    queryKey: ["ai-session-history", userId, sessionId],
    queryFn: async () => {
      const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}/${encodeURIComponent(userId)}/${sessionId}`;
      const authHeaders = buildAuthHeaders();
      const response = await fetch(url, { headers: authHeaders });
      if (!response.ok)
        throw new Error(`Failed to fetch session: ${response.status}`);
      return response.json();
    },
    enabled: !!sessionId,
    refetchOnWindowFocus: false,
  });
};
