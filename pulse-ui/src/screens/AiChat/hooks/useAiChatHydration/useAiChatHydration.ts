import { useCallback, useEffect, useRef } from "react";
import { v4 as uuidV4 } from "uuid";
import { useChatStore } from "../../../../stores/useChatStore";
import { useCreateUserAiSession } from "../useCreateUserAiSession";
import { useGetAiSessions } from "../useGetAiSessions";
import { useGetAiSessionHistory } from "../useGetAiSessionHistory";
import {
  AiChartConfig,
  AiTableConfig,
  ChatMessage,
  ChatSession,
} from "../../types/chat";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";
import { toMs } from "../../AiChat.utils";

export const useAiChatHydration = () => {
  const {
    sessions,
    activeSessionId,
    createSession,
    switchSession,
    setSessions,
    setMessages,
    setError,
  } = useChatStore();

  const { mutate: createAdkSession } = useCreateUserAiSession((_data, err) => {
    if (err) setError(AI_CHAT_TEXTS.ERROR_GENERIC);
  });

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

  useEffect(() => {
    if (!historyData || !activeSessionId) return;
    if (hydratedRef.current.has(activeSessionId)) return;
    hydratedRef.current.add(activeSessionId);

    const mapped: ChatMessage[] = historyData.messages.map((m, i) => ({
      id: `restored-${i}`,
      role: m.role as "user" | "model",
      text: m.text,
      charts: m.charts?.length ? (m.charts as AiChartConfig[]) : undefined,
      tables: m.tables?.length ? (m.tables as AiTableConfig[]) : undefined,
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

  return { handleNewChat, isLoadingSessions };
};
