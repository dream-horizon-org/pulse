import { useCallback } from "react";
import { v4 as uuidV4 } from "uuid";
import { useChatStore } from "../../../../stores/useChatStore";
import { useGetPulseAiResponse } from "../useGetPulseAiResponse";
import { ChatMessage } from "../../types/chat";
import { AI_CHAT_TEXTS, AI_CHAT_LIMITS } from "../../AiChat.constants";

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
    updateSessionTitle,
    setStreaming,
    setError,
  } = useChatStore();

  const { sendMessage, cancel } = useGetPulseAiResponse();

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

      const sid = activeSessionId;
      sendMessage(sid, text, {
        onToken: (token) => {
          appendToLastMessage(sid, token);
        },
        onCharts: (charts) => {
          updateLastMessageCharts(sid, charts);
        },
        onTables: (tables) => {
          updateLastMessageTables(sid, tables);
        },
        onComplete: () => {
          setStreaming(false);
          markLastMessageComplete(sid);
        },
        onError: (errMsg) => {
          setStreaming(false);
          setError(errMsg);
          markLastMessageError(sid, AI_CHAT_TEXTS.FAILED_RESPONSE);
        },
      });
    },
    [
      activeSessionId,
      sessions,
      addMessage,
      appendToLastMessage,
      updateLastMessageCharts,
      updateLastMessageTables,
      markLastMessageComplete,
      markLastMessageError,
      updateSessionTitle,
      setStreaming,
      setError,
      sendMessage,
    ],
  );

  return { handleSend, cancel };
};
