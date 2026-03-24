import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { makeRequestToServer } from "../../../../helpers/makeRequestToServer";
import type {
  CreateSessionInput,
  OnSettled,
  UseCreateUserAiSessionResponse,
} from "./useCreateUserAiSession.interface";
import { AI_API_PATHS } from "../../AiChat.constants";

async function createUserAiSessionOnServer(
  signal: AbortSignal | undefined,
): Promise<UseCreateUserAiSessionResponse> {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";
  const params = new URLSearchParams({ user_id: userId });
  const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}?${params.toString()}`;
  const response = await makeRequestToServer({
    url,
    init: { method: "POST", signal },
    unwrapped: true,
  });
  if (!response.ok) {
    throw new Error(`Failed to create session: ${response.status}`);
  }
  const json: unknown = await response.json();
  if (
    !json ||
    typeof json !== "object" ||
    !("session_id" in json) ||
    typeof (json as { session_id: unknown }).session_id !== "string" ||
    !(json as { session_id: string }).session_id.length
  ) {
    throw new Error("Invalid create session response: missing session_id");
  }
  const { session_id, user_id } = json as {
    session_id: string;
    user_id?: string;
  };
  return {
    session_id,
    user_id: user_id ?? userId,
  };
}

/**
 * Creates an ADK session on the server (pulse_ai via proxy). The UI does not send
 * session_id; the response provides session_id.
 *
 * TanStack Query v5 passes only `variables` to mutationFn, so we use an
 * AbortController per call and abort on unmount / when superseded by a new call.
 */
export const useCreateUserAiSession = (onSettled: OnSettled) => {
  const abortRef = useRef<AbortController | null>(null);

  useEffect(
    () => () => {
      abortRef.current?.abort();
    },
    [],
  );

  return useMutation<
    UseCreateUserAiSessionResponse,
    unknown,
    CreateSessionInput
  >({
    mutationFn: async (_vars: CreateSessionInput) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      try {
        return await createUserAiSessionOnServer(controller.signal);
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
      }
    },
    onSettled,
  });
};
