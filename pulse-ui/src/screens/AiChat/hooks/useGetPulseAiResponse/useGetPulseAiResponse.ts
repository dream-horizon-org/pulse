// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway (SSE streaming may need a custom adapter)
import { useCallback, useRef } from "react";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import {
  StreamingCallbacks,
  UseGetPulseAiResponseReturn,
} from "./useGetPulseAiResponse.interface";
import { readSseStream } from "./sseParser";
import { AI_API_PATHS, AI_CHAT_TEXTS } from "../../AiChat.constants";

export const useGetPulseAiResponse = (): UseGetPulseAiResponseReturn => {
  const abortControllerRef = useRef<AbortController | null>(null);

  const cancel = useCallback(() => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
  }, []);

  const sendMessage = useCallback(
    (sessionId: string, text: string, callbacks: StreamingCallbacks) => {
      const controller = new AbortController();
      abortControllerRef.current = controller;

      const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

      const body = JSON.stringify({
        user_id: userId,
        session_id: sessionId,
        new_message: { role: "user", parts: [{ text }] },
        streaming: true,
      });

      fetch(`${AI_BASE_URL}${AI_API_PATHS.RUN_SSE}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok) {
            callbacks.onError(`Server error: ${response.status}`);
            return;
          }
          if (!response.body) {
            callbacks.onError(AI_CHAT_TEXTS.NO_RESPONSE_BODY);
            return;
          }

          try {
            await readSseStream(response.body.getReader(), callbacks);
          } catch (err) {
            if ((err as Error).name === "AbortError") return;
            callbacks.onError(
              (err as Error).message || AI_CHAT_TEXTS.STREAM_FAILED,
            );
          }
        })
        .catch((err) => {
          if ((err as Error).name === "AbortError") return;
          callbacks.onError(
            (err as Error).message || AI_CHAT_TEXTS.NETWORK_ERROR,
          );
        });
    },
    [],
  );

  return { sendMessage, cancel };
};
