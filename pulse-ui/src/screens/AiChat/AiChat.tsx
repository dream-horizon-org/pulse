import { useCallback, useEffect, useRef } from "react";
import { Alert, Box } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { v4 as uuidV4 } from "uuid";
import { useChatStore } from "../../stores/useChatStore";
import { useCreateUserAiSession } from "./hooks/useCreateUserAiSession";
import { useGetPulseAiResponse } from "./hooks/useGetPulseAiResponse";
import { useGetAiSessions } from "./hooks/useGetAiSessions";
import { useGetAiSessionHistory } from "./hooks/useGetAiSessionHistory";
import { ChatSidebar } from "./components/ChatSidebar";
import { ChatMessageList } from "./components/ChatMessageList";
import { ChatInput } from "./components/ChatInput";
import { AiChartConfig, AiTableConfig, ChatMessage, ChatSession } from "./types/chat";
import { AI_CHAT_TEXTS } from "./AiChat.constants";
import { toMs } from "./AiChat.utils";
import classes from "./AiChat.module.css";

export const AiChat = () => {
  const {
    sessions,
    activeSessionId,
    messages,
    isStreaming,
    error,
    createSession,
    switchSession,
    setSessions,
    setMessages,
    addMessage,
    updateLastMessage,
    updateLastMessageCharts,
    updateLastMessageTables,
    setStreaming,
    setError,
  } = useChatStore();

  const { mutate: createAdkSession } = useCreateUserAiSession(
    (data, err) => {
      if (err) setError(AI_CHAT_TEXTS.ERROR_GENERIC);
    },
  );

  const { sendMessage, cancel } = useGetPulseAiResponse();

  const hydratedRef = useRef<Set<string>>(new Set());

  const {
    data: sessionsData,
    isLoading: isLoadingSessions,
    isError: isSessionsError,
  } = useGetAiSessions();

  const shouldFetchHistory =
    !!activeSessionId && !hydratedRef.current.has(activeSessionId);
  const { data: historyData } = useGetAiSessionHistory(
    shouldFetchHistory ? activeSessionId : null,
  );

  const activeMessages = activeSessionId ? messages[activeSessionId] ?? [] : [];

  const handleNewChat = useCallback(() => {
    const sessionId = uuidV4();
    const session: ChatSession = {
      id: sessionId,
      title: "New conversation",
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    createSession(session);
    createAdkSession({ sessionId });
    hydratedRef.current.add(sessionId);
  }, [createSession, createAdkSession]);

  // Hydrate session list from backend (or fall back to creating a new one)
  useEffect(() => {
    if (isLoadingSessions) return;

    if (isSessionsError) {
      if (sessions.length === 0) handleNewChat();
      return;
    }

    if (!sessionsData || hydratedRef.current.has("__sessions__")) return;
    hydratedRef.current.add("__sessions__");

    if (sessionsData.length === 0) {
      if (sessions.length === 0) handleNewChat();
      return;
    }

    const mapped: ChatSession[] = sessionsData.map((s) => ({
      id: s.id,
      title: s.title || "New conversation",
      createdAt: toMs(s.last_update_time),
      updatedAt: toMs(s.last_update_time),
    }));
    setSessions(mapped);
    switchSession(mapped[0].id);
  }, [sessionsData, isLoadingSessions, isSessionsError]); // eslint-disable-line react-hooks/exhaustive-deps

  // Hydrate messages when switching to a backend session
  useEffect(() => {
    if (!historyData || !activeSessionId) return;
    if (hydratedRef.current.has(activeSessionId)) return;
    hydratedRef.current.add(activeSessionId);

    const mapped: ChatMessage[] = historyData.messages.map((m, i) => ({
      id: `restored-${i}`,
      role: m.role as "user" | "model",
      text: m.text,
      charts: m.charts?.length
        ? (m.charts as AiChartConfig[])
        : undefined,
      tables: m.tables?.length
        ? (m.tables as AiTableConfig[])
        : undefined,
      timestamp: toMs(historyData.last_update_time),
    }));
    setMessages(activeSessionId, mapped);

    const firstUserMsg = historyData.messages.find((m) => m.role === "user");
    if (firstUserMsg) {
      const store = useChatStore.getState();
      useChatStore.setState({
        sessions: store.sessions.map((s) =>
          s.id === activeSessionId
            ? { ...s, title: firstUserMsg.text.slice(0, 50) }
            : s,
        ),
      });
    }
  }, [historyData, activeSessionId, setMessages]);

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
          s.id === activeSessionId
            ? { ...s, title: text.slice(0, 50) }
            : s,
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
              ? { ...m, isStreaming: false, text: m.text || "Failed to get response." }
              : m,
          );
          useChatStore.setState({
            messages: { ...store.messages, [sid]: updatedMsgs },
          });
        },
      });
    },
    [activeSessionId, sessions, addMessage, updateLastMessage, updateLastMessageCharts, updateLastMessageTables, setStreaming, setError, sendMessage],
  );

  return (
    <Box className={classes.container}>
      <ChatSidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        isLoading={isLoadingSessions}
        onNewChat={handleNewChat}
        onSelectSession={switchSession}
      />
      <Box className={classes.chatArea}>
        {error && (
          <Alert
            icon={<IconAlertCircle size={16} />}
            color="red"
            variant="light"
            withCloseButton
            onClose={() => setError(null)}
            className={classes.errorBar}
          >
            {error}
          </Alert>
        )}
        <ChatMessageList
          messages={activeMessages}
          onSelectSuggestion={handleSend}
        />
        <ChatInput onSend={handleSend} isStreaming={isStreaming} />
      </Box>
    </Box>
  );
};
