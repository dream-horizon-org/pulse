import { useMutation } from "@tanstack/react-query";
import { useCallback, useRef } from "react";
import { COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { streamAiRunSseWithAuth } from "../../../../helpers/makeRequest";
import {
  StreamingCallbacks,
  UseGetPulseAiResponseReturn,
} from "./useGetPulseAiResponse.interface";
import { readSseStream } from "./sseParser";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";

/** DOM fetch error names (e.g. when request is aborted via AbortController). */
enum FetchErrorName {
  Abort = "AbortError",
}

type StreamAiMessageVariables = {
  sessionId: string;
  text: string;
  callbacks: StreamingCallbacks;
};

export const useGetPulseAiResponse = (): UseGetPulseAiResponseReturn => {
  const abortControllerRef = useRef<AbortController | null>(null);

  const { mutate, reset } = useMutation({
    mutationFn: async ({
      sessionId,
      text,
      callbacks,
    }: StreamAiMessageVariables): Promise<void> => {
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;

      const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";
      const body = JSON.stringify({
        user_id: userId,
        session_id: sessionId,
        new_message: { role: "user", parts: [{ text }] },
        streaming: true,
      });

      try {
        const response = await streamAiRunSseWithAuth({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body,
          signal: controller.signal,
        });

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
          if ((err as Error).name === FetchErrorName.Abort) return;
          callbacks.onError(
            (err as Error).message || AI_CHAT_TEXTS.STREAM_FAILED,
          );
        }
      } catch (err) {
        if ((err as Error).name === FetchErrorName.Abort) return;
        callbacks.onError(
          (err as Error).message || AI_CHAT_TEXTS.NETWORK_ERROR,
        );
      } finally {
        if (abortControllerRef.current === controller) {
          abortControllerRef.current = null;
        }
      }
    },
  });

  const cancel = useCallback(() => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    reset();
  }, [reset]);

  const sendMessage = useCallback(
    (sessionId: string, text: string, callbacks: StreamingCallbacks) => {
      mutate({ sessionId, text, callbacks });
    },
    [mutate],
  );

  return { sendMessage, cancel };
};
