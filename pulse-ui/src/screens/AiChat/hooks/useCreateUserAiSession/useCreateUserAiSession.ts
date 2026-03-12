// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { buildAuthHeaders } from "../../../../helpers/makeRequestToServer";
import {
  CreateSessionInput,
  OnSettled,
  UseCreateUserAiSessionResponse,
} from "./useCreateUserAiSession.interface";
import { AI_API_PATHS } from "../../AiChat.constants";

export const useCreateUserAiSession = (onSettled: OnSettled) => {
  return useMutation<
    UseCreateUserAiSessionResponse,
    unknown,
    CreateSessionInput
  >({
    mutationFn: async (input: CreateSessionInput) => {
      const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

      const params = new URLSearchParams({
        user_id: userId,
        session_id: input.sessionId,
      });

      const authHeaders = buildAuthHeaders();

      const response = await fetch(
        `${API_BASE_URL}${AI_API_PATHS.SESSIONS}?${params.toString()}`,
        { method: "POST", headers: authHeaders },
      );

      if (!response.ok) {
        throw new Error(`Failed to create session: ${response.status}`);
      }

      return response.json();
    },
    onSettled,
  });
};
