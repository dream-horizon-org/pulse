import { useCallback, useEffect, useRef } from "react";
import { v4 as uuidV4 } from "uuid";
import { useChatStore } from "../../../../stores/useChatStore";
import { useGetPulseAiResponse } from "../useGetPulseAiResponse";
import type { AiChartConfig, AiTableConfig } from "../../types/chat";
import { ChatMessage } from "../../types/chat";
import { AI_CHAT_TEXTS, AI_CHAT_LIMITS } from "../../AiChat.constants";

/** Characters displayed per animation tick — controls typewriter speed. */
const TYPEWRITER_CHARS_PER_TICK = 4;
/** Milliseconds between animation ticks — 16ms ≈ 60 fps. */
const TYPEWRITER_TICK_MS = 16;

export const useHandleSend = () => {
  const {
    activeSessionId,
    sessions,
    addMessage,
    appendToLastMessage,
    updateLastMessageCharts,
    updateLastMessageTables,
    markLastMessageComplete,
    markLastMessageError,
    patchLastMessageIdByRole,
    updateSessionTitle,
    setStreaming,
    setError,
  } = useChatStore();

  const { sendMessage, cancel } = useGetPulseAiResponse();

  // Display-queue refs — survive re-renders, never stale.
  const tokenQueueRef = useRef<string[]>([]);
  const streamDoneRef = useRef(false);
  const typewriterRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** Charts/tables from SSE are held here until the typewriter queue drains so visuals appear after text. */
  const pendingChartsRef = useRef<AiChartConfig[] | null>(null);
  const pendingTablesRef = useRef<AiTableConfig[] | null>(null);

  const stopTypewriter = useCallback(() => {
    if (typewriterRef.current !== null) {
      clearInterval(typewriterRef.current);
      typewriterRef.current = null;
    }
  }, []);

  const flushPendingVisuals = useCallback(
    (sid: string) => {
      const charts = pendingChartsRef.current;
      const tables = pendingTablesRef.current;
      if (charts === null && tables === null) return;
      if (charts !== null) {
        updateLastMessageCharts(sid, charts);
        pendingChartsRef.current = null;
      }
      if (tables !== null) {
        updateLastMessageTables(sid, tables);
        pendingTablesRef.current = null;
      }
    },
    [updateLastMessageCharts, updateLastMessageTables],
  );

  /**
   * Start the display-queue drain interval for a given session.
   * Safe to call multiple times — no-ops if already running.
   */
  const startTypewriter = useCallback(
    (sid: string) => {
      if (typewriterRef.current !== null) return;
      typewriterRef.current = setInterval(() => {
        if (tokenQueueRef.current.length === 0) {
          // Nothing left to display — finalise if stream is also done.
          if (streamDoneRef.current) {
            flushPendingVisuals(sid);
            stopTypewriter();
            setStreaming(false);
            markLastMessageComplete(sid);
          }
          return;
        }

        // Drain up to TYPEWRITER_CHARS_PER_TICK characters from the front of the queue.
        let charsRemaining = TYPEWRITER_CHARS_PER_TICK;
        let toDisplay = "";
        while (tokenQueueRef.current.length > 0 && charsRemaining > 0) {
          const head = tokenQueueRef.current[0];
          if (head.length <= charsRemaining) {
            toDisplay += tokenQueueRef.current.shift()!;
            charsRemaining -= head.length;
          } else {
            toDisplay += head.slice(0, charsRemaining);
            tokenQueueRef.current[0] = head.slice(charsRemaining);
            charsRemaining = 0;
          }
        }
        if (toDisplay) {
          appendToLastMessage(sid, toDisplay);
        }
      }, TYPEWRITER_TICK_MS);
    },
    [
      stopTypewriter,
      setStreaming,
      markLastMessageComplete,
      appendToLastMessage,
      flushPendingVisuals,
    ],
  );

  const handleSend = useCallback(
    (text: string) => {
      if (!activeSessionId) return;

      const userMsg: ChatMessage = {
        id: uuidV4(),
        role: "user",
        text,
        timestamp: Date.now(),
      };
      addMessage(activeSessionId, userMsg);

      if (
        sessions.find((s) => s.id === activeSessionId)?.title ===
        AI_CHAT_TEXTS.NEW_CONVERSATION
      ) {
        updateSessionTitle(
          activeSessionId,
          text.slice(0, AI_CHAT_LIMITS.TITLE_MAX_LENGTH),
        );
      }

      const aiMsg: ChatMessage = {
        id: uuidV4(),
        role: "model",
        text: "",
        timestamp: Date.now(),
        isStreaming: true,
      };
      addMessage(activeSessionId, aiMsg);
      setStreaming(true);
      setError(null);

      // Reset display queue state for this send.
      tokenQueueRef.current = [];
      streamDoneRef.current = false;
      pendingChartsRef.current = null;
      pendingTablesRef.current = null;
      stopTypewriter();

      const sid = activeSessionId;
      sendMessage(sid, text, {
        onMeta: ({ userEventId, assistantEventId }) => {
          if (userEventId) {
            patchLastMessageIdByRole(sid, "user", userEventId);
          }
          if (assistantEventId) {
            patchLastMessageIdByRole(sid, "model", assistantEventId);
          }
        },
        onToken: (token) => {
          // Enqueue raw SSE chunk; the typewriter interval drains it at a
          // controlled rate regardless of how fast chunks arrive from the server.
          tokenQueueRef.current.push(token);
          startTypewriter(sid);
        },
        onCharts: (charts) => {
          pendingChartsRef.current = charts;
        },
        onTables: (tables) => {
          pendingTablesRef.current = tables;
        },
        onComplete: () => {
          // Mark stream as done. The interval will finalise once the queue drains.
          streamDoneRef.current = true;
          if (tokenQueueRef.current.length === 0) {
            flushPendingVisuals(sid);
            stopTypewriter();
            setStreaming(false);
            markLastMessageComplete(sid);
          }
        },
        onError: (errMsg) => {
          console.error("[Pulse AI]", errMsg);
          stopTypewriter();
          tokenQueueRef.current = [];
          streamDoneRef.current = false;
          pendingChartsRef.current = null;
          pendingTablesRef.current = null;
          const display =
            typeof errMsg === "string" && errMsg.trim().length > 0
              ? errMsg.trim().slice(0, 2000)
              : AI_CHAT_TEXTS.ERROR_GENERIC;
          setStreaming(false);
          setError(display);
          markLastMessageError(sid, display);
        },
      });
    },
    [
      activeSessionId,
      sessions,
      addMessage,
      appendToLastMessage,
      flushPendingVisuals,
      markLastMessageComplete,
      markLastMessageError,
      patchLastMessageIdByRole,
      updateSessionTitle,
      setStreaming,
      setError,
      sendMessage,
      startTypewriter,
      stopTypewriter,
    ],
  );

  useEffect(() => {
    return () => {
      pendingChartsRef.current = null;
      pendingTablesRef.current = null;
      stopTypewriter();
      cancel();
    };
  }, [stopTypewriter, cancel]);

  return { handleSend, cancel };
};
