// TODO: Replace raw fetch with makeRequest helper once AI backend is behind the shared API gateway (SSE streaming may need a custom adapter)
import { useCallback, useRef } from "react";
import { AI_BASE_URL, COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import {
  StreamingCallbacks,
  UseGetPulseAiResponseReturn,
} from "./useGetPulseAiResponse.interface";

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

      const userId =
        getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

      const body = JSON.stringify({
        user_id: userId,
        session_id: sessionId,
        new_message: {
          role: "user",
          parts: [{ text }],
        },
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

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";

          try {
            while (true) {
              const { done, value } = await reader.read();
              if (done) break;

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split("\n");
              buffer = lines.pop() || "";

              for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed.startsWith("data: ")) continue;

                const payload = trimmed.slice(6);

                if (payload === "[DONE]") {
                  callbacks.onComplete();
                  return;
                }

                try {
                  const parsed = JSON.parse(payload);

                  if (parsed.type === "text" && parsed.content) {
                    callbacks.onToken(parsed.content);
                  } else if (
                    parsed.type === "content_blocks" &&
                    Array.isArray(parsed.blocks)
                  ) {
                    const charts = parsed.blocks
                      .filter((b: Record<string, unknown>) => b.block_type === "chart")
                      .map(({ block_type, ...rest }: Record<string, unknown>) => rest);
                    const tables = parsed.blocks
                      .filter((b: Record<string, unknown>) => b.block_type === "table")
                      .map(({ block_type, ...rest }: Record<string, unknown>) => rest);
                    if (charts.length) callbacks.onCharts(charts);
                    if (tables.length) callbacks.onTables(tables);
                    callbacks.onContentBlocks?.(parsed.blocks);
                  } else if (parsed.type === "error") {
                    callbacks.onError(
                      parsed.message || "Unknown agent error",
                    );
                  }
                } catch {
                  // Non-JSON SSE line, skip
                }
              }
            }

            callbacks.onComplete();
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
