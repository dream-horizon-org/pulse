// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway (SSE streaming may need a custom adapter)
import { useCallback, useRef } from "react";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import {
  StreamingCallbacks,
  UseGetPulseAiResponseReturn,
} from "./useGetPulseAiResponse.interface";
import { readSseStream } from "./sseParser";

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

      fetch(`${AI_BASE_URL}/run_sse`, {
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
            callbacks.onError("No response body");
            return;
          }

          try {
            await readSseStream(response.body.getReader(), callbacks);
          } catch (err) {
            if ((err as Error).name === "AbortError") return;
            callbacks.onError(
              (err as Error).message || "Stream reading failed",
            );
          }
        })
        .catch((err) => {
          if ((err as Error).name === "AbortError") return;
          callbacks.onError((err as Error).message || "Network error");
        });
    },
    [],
  );

  return { sendMessage, cancel };
};
