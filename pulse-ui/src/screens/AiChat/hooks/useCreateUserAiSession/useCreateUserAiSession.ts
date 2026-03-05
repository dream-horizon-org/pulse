// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway
import { useMutation } from "@tanstack/react-query";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import {
  CreateSessionInput,
  OnSettled,
  UseCreateUserAiSessionResponse,
} from "./useCreateUserAiSession.interface";

export const useCreateUserAiSession = (onSettled: OnSettled) => {
  return useMutation<UseCreateUserAiSessionResponse, unknown, CreateSessionInput>({
    mutationFn: async (input: CreateSessionInput) => {
      const userId =
        getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

      const params = new URLSearchParams({
        user_id: userId,
        session_id: input.sessionId,
      });

      const response = await fetch(
        `${AI_BASE_URL}/sessions?${params.toString()}`,
        { method: "POST" },
      );

      if (!response.ok) {
        throw new Error(`Failed to create session: ${response.status}`);
      }

      return response.json();
    },
    onSettled,
  });
};
