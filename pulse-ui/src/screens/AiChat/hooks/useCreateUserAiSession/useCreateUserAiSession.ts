import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { API_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { makeRequest } from "../../../../helpers/makeRequest";
import type {
  CreateSessionBody,
  CreateSessionInput,
  OnSettled,
  UseCreateUserAiSessionResponse,
} from "./useCreateUserAiSession.interface";
import { AI_API_PATHS } from "../../AiChat.constants";
import { parseCreateSessionBody } from "./parseCreateSessionBody";

async function createUserAiSessionOnServer(
  signal: AbortSignal | undefined,
): Promise<UseCreateUserAiSessionResponse> {
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";
  const params = new URLSearchParams({ user_id: userId });
  const url = `${API_BASE_URL}${AI_API_PATHS.SESSIONS}?${params.toString()}`;
  const result = await makeRequest<CreateSessionBody>({
    url,
    init: { method: "POST", signal },
    unwrapped: true,
  });
  const hasError = result.error != null;
  if (hasError) {
    const message =
      result.error?.message ?? `Failed to create session: ${result.status}`;
    throw new Error(message);
  }
  const json: unknown = result.data;
  const dataMissing = json == null;
  if (dataMissing) {
    throw new Error(`Failed to create session: ${result.status}`);
  }
  const { session_id, user_id } = parseCreateSessionBody(json);
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
