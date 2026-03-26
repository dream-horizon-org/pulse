import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef } from "react";
import { COOKIES_KEY } from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { useChatStore } from "../../../../stores/useChatStore";
import { useCreateUserAiSession } from "../useCreateUserAiSession";
import { useGetAiSessions } from "../useGetAiSessions";
import { useGetAiSessionHistory } from "../useGetAiSessionHistory";
import type { AiSessionListItem } from "../useGetAiSessions";
import {
  AiChartConfig,
  AiTableConfig,
  ChatMessage,
  ChatRole,
  ChatSession,
} from "../../types/chat";
import { AI_CHAT_TEXTS, AI_CHAT_LIMITS } from "../../AiChat.constants";
import { toMs } from "../../AiChat.utils";

export const useAiChatHydration = () => {
  const {
    sessions,
    activeSessionId,
    createSession,
    switchSession,
    setSessions,
    setMessages,
    updateSessionTitle,
    setError,
    error,
  } = useChatStore();

  const queryClient = useQueryClient();
  const userId = getCookies(COOKIES_KEY.USER_EMAIL) || "anonymous";

  const { mutateAsync: createSessionOnServer, isPending: isCreatingSession } =
    useCreateUserAiSession((_data, err) => {
      if (err) setError(AI_CHAT_TEXTS.SESSION_CREATE_FAILED);
    });

  const hydratedRef = useRef<Set<string>>(new Set());
  const sessionsHydratedRef = useRef(false);

  const {
    data: sessionsData,
    isLoading: isLoadingSessions,
    isError: isSessionsError,
    refetchSessions,
  } = useGetAiSessions();

  const shouldFetchHistory =
    !!activeSessionId && !hydratedRef.current.has(activeSessionId);
  const { data: historyData } = useGetAiSessionHistory(
    shouldFetchHistory ? activeSessionId : null,
  );

  /**
   * Optimistic list sync: server is source of truth for `session_id`, but we prepend the
   * new row into React Query immediately so the sidebar updates without waiting for refetch.
   */
  const prependSessionToQueryCache = useCallback(
    (row: AiSessionListItem) => {
      queryClient.setQueryData<AiSessionListItem[]>(
        ["ai-sessions", userId],
        (prev) => [row, ...(prev ?? [])],
      );
    },
    [queryClient, userId],
  );

  /**
   * Creates session on pulse_ai (via proxy), then syncs React Query cache + Zustand so the
   * UI shows the new thread immediately; server remains source of truth for `session_id`.
   */
  const createLocalSessionFromServer = useCallback(async () => {
    const data = await createSessionOnServer({});
    const nowSec = Math.floor(Date.now() / 1000);
    const listRow: AiSessionListItem = {
      id: data.session_id,
      user_id: data.user_id,
      title: AI_CHAT_TEXTS.NEW_CONVERSATION,
      last_update_time: nowSec,
    };
    prependSessionToQueryCache(listRow);
    const session: ChatSession = {
      id: data.session_id,
      title: AI_CHAT_TEXTS.NEW_CONVERSATION,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    createSession(session);
    hydratedRef.current.add(data.session_id);
    return data;
  }, [createSession, createSessionOnServer, prependSessionToQueryCache]);

  const handleNewChat = useCallback(async () => {
    try {
      await createLocalSessionFromServer();
    } catch {
      // onSettled already surfaces ERROR_GENERIC
    }
  }, [createLocalSessionFromServer]);

  const onRetrySessions = useCallback(() => {
    setError(null);
    void refetchSessions();
  }, [refetchSessions, setError]);

  useEffect(() => {
    if (!isSessionsError && error === AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED) {
      setError(null);
    }
  }, [isSessionsError, error, setError]);

  useEffect(() => {
    if (!isLoadingSessions && isSessionsError) {
      setError(AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED);
    }
  }, [isLoadingSessions, isSessionsError, setError]);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      if (isLoadingSessions) return;

      if (isSessionsError) return;

      if (!sessionsData || sessionsHydratedRef.current) return;

      if (sessionsData.length === 0 && sessions.length === 0) {
        try {
          await createLocalSessionFromServer();
          if (cancelled) return;
          sessionsHydratedRef.current = true;
        } catch {
          if (!cancelled) sessionsHydratedRef.current = true;
        }
        return;
      }

      if (sessionsData.length === 0) {
        sessionsHydratedRef.current = true;
        return;
      }

      sessionsHydratedRef.current = true;
      const mapped: ChatSession[] = sessionsData.map((s) => ({
        id: s.id,
        title: s.title || AI_CHAT_TEXTS.NEW_CONVERSATION,
        createdAt: toMs(s.last_update_time),
        updatedAt: toMs(s.last_update_time),
      }));
      setSessions(mapped);
      switchSession(mapped[0].id);
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [
    sessionsData,
    isLoadingSessions,
    isSessionsError,
    sessions.length,
    createLocalSessionFromServer,
    setSessions,
    switchSession,
  ]);

  useEffect(() => {
    if (!historyData || !activeSessionId) return;
    if (hydratedRef.current.has(activeSessionId)) return;
    hydratedRef.current.add(activeSessionId);

    const rawMessages = historyData.messages ?? [];
    const mapped: ChatMessage[] = rawMessages.map((m, i) => ({
      id: typeof m.id === "string" && m.id.length > 0 ? m.id : `restored-${i}`,
      role: m.role as ChatRole,
      text: m.text ?? "",
      charts: m.charts?.length ? (m.charts as AiChartConfig[]) : undefined,
      tables: m.tables?.length ? (m.tables as AiTableConfig[]) : undefined,
      timestamp: toMs(historyData.last_update_time),
    }));
    setMessages(activeSessionId, mapped);

    const firstUserMsg = rawMessages.find((m) => m.role === "user");
    if (firstUserMsg) {
      updateSessionTitle(
        activeSessionId,
        (firstUserMsg.text ?? "").slice(0, AI_CHAT_LIMITS.TITLE_MAX_LENGTH),
      );
    }
  }, [historyData, activeSessionId, setMessages, updateSessionTitle]);

  return {
    handleNewChat,
    isLoadingSessions,
    isSessionsError,
    onRetrySessions,
    isCreatingSession,
    sessionsErrorMessage: isSessionsError
      ? AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED
      : null,
  };
};
