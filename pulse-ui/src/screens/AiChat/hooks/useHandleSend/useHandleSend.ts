import { useCallback } from "react";
import { v4 as uuidV4 } from "uuid";
import { useChatStore } from "../../../../stores/useChatStore";
import { useGetPulseAiResponse } from "../useGetPulseAiResponse";
import { ChatMessage } from "../../types/chat";

export const useHandleSend = () => {
  const {
    activeSessionId,
    sessions,
    addMessage,
    updateLastMessage,
    updateLastMessageCharts,
    updateLastMessageTables,
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
        "New conversation"
      ) {
        const store = useChatStore.getState();
        const updatedSessions = store.sessions.map((s) =>
          s.id === activeSessionId ? { ...s, title: text.slice(0, 50) } : s,
        );
        useChatStore.setState({ sessions: updatedSessions });
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
          const store = useChatStore.getState();
          const sessionMsgs = store.messages[sid] ?? [];
          const last = sessionMsgs[sessionMsgs.length - 1];
          if (last?.role === "model") {
            updateLastMessage(sid, last.text + token);
          }
        },
        onCharts: (charts) => {
          updateLastMessageCharts(sid, charts);
        },
        onTables: (tables) => {
          updateLastMessageTables(sid, tables);
        },
        onComplete: () => {
          setStreaming(false);
          const store = useChatStore.getState();
          const sessionMsgs = store.messages[sid] ?? [];
          const updatedMsgs = sessionMsgs.map((m, i) =>
            i === sessionMsgs.length - 1 ? { ...m, isStreaming: false } : m,
          );
          useChatStore.setState({
            messages: { ...store.messages, [sid]: updatedMsgs },
          });
        },
        onError: (errMsg) => {
          setStreaming(false);
          setError(errMsg);
          const store = useChatStore.getState();
          const sessionMsgs = store.messages[sid] ?? [];
          const updatedMsgs = sessionMsgs.map((m, i) =>
            i === sessionMsgs.length - 1
              ? {
                  ...m,
                  isStreaming: false,
                  text: m.text || "Failed to get response.",
                }
              : m,
          );
          useChatStore.setState({
            messages: { ...store.messages, [sid]: updatedMsgs },
          });
        },
      });
    },
    [
      activeSessionId,
      sessions,
      addMessage,
      updateLastMessage,
      updateLastMessageCharts,
      updateLastMessageTables,
      setStreaming,
      setError,
      sendMessage,
    ],
  );

  return { handleSend, cancel };
};
