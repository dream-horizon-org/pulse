/**
 * Manages multiple AI chat sessions with URL sync and Chrome-like tabs.
 * - Session ID in URL (?s=xxx)
 * - Multiple tabs (sessions)
 * - Persist to localStorage + API for sharing
 */

import { useState, useCallback, useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { ChatMessage } from "../components/AiChat/AiChat.interface";
import {
  AiChatSessionData,
  generateSessionId,
  loadSessionFromLocal,
  saveSessionToLocal,
  fetchSessionFromApi,
  saveSessionToApi,
  deleteSessionFromLocal,
} from "../services/aiChatSessionStorage";

const SEARCH_PARAM = "s";

export interface AiSessionTab {
  id: string;
  title: string;
  createdAt: number;
}

export interface UseAiChatSessionManagerReturn {
  /** All session tabs */
  tabs: AiSessionTab[];
  /** Currently active session ID */
  activeSessionId: string | null;
  /** Session data for the active session (null if loading) */
  activeSession: AiChatSessionData | null;
  /** Whether we're loading a shared session from API */
  isLoadingShared: boolean;
  /** Create a new session tab */
  createSession: () => string;
  /** Switch to a session by ID */
  switchSession: (sessionId: string) => void;
  /** Close a session tab */
  closeSession: (sessionId: string) => void;
  /** Persist session state (call when messages/pins change) */
  persistSession: (data: Omit<AiChatSessionData, "id" | "createdAt" | "updatedAt">) => void;
  /** Get full session data for a given ID */
  getSessionData: (sessionId: string) => AiChatSessionData | null;
}

function getTabTitle(messages: ChatMessage[]): string {
  const firstUser = messages.find((m) => m.role === "user");
  if (firstUser?.content) {
    const text = firstUser.content.trim();
    return text.length > 30 ? text.slice(0, 27) + "..." : text;
  }
  return "New chat";
}

export function useAiChatSessionManager(): UseAiChatSessionManagerReturn {
  const [searchParams, setSearchParams] = useSearchParams();
  const sessionIdFromUrl = searchParams.get(SEARCH_PARAM);

  const [tabs, setTabs] = useState<AiSessionTab[]>([]);
  const [sessionsData, setSessionsData] = useState<Map<string, AiChatSessionData>>(new Map());
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [isLoadingShared, setIsLoadingShared] = useState(false);
  const initialLoadDone = useRef(false);

  // Load session from URL on mount / URL change
  useEffect(() => {
    if (!sessionIdFromUrl) {
      initialLoadDone.current = true;
      return;
    }

    const load = async () => {
      // 1. Try localStorage first
      let data = loadSessionFromLocal(sessionIdFromUrl);
      if (data) {
        setSessionsData((prev) => new Map(prev).set(sessionIdFromUrl, data!));
        setTabs((prev) => {
          if (prev.some((t) => t.id === sessionIdFromUrl)) return prev;
          return [
            ...prev,
            {
              id: sessionIdFromUrl,
              title: data!.title ?? getTabTitle(data!.messages),
              createdAt: data!.createdAt,
            },
          ];
        });
        setActiveSessionId(sessionIdFromUrl);
        initialLoadDone.current = true;
        return;
      }

      // 2. Try API (for shared URLs)
      setIsLoadingShared(true);
      data = await fetchSessionFromApi(sessionIdFromUrl);
      setIsLoadingShared(false);
      if (data) {
        saveSessionToLocal(data);
        setSessionsData((prev) => new Map(prev).set(sessionIdFromUrl, data!));
        setTabs((prev) => {
          if (prev.some((t) => t.id === sessionIdFromUrl)) return prev;
          return [
            ...prev,
            {
              id: sessionIdFromUrl,
              title: data!.title ?? getTabTitle(data!.messages),
              createdAt: data!.createdAt,
            },
          ];
        });
        setActiveSessionId(sessionIdFromUrl);
      } else {
        // Unknown session ID - create new tab with this ID
        const now = Date.now();
        const newSession: AiChatSessionData = {
          id: sessionIdFromUrl,
          messages: [],
          pinnedFindings: [],
          selectedMessageId: null,
          createdAt: now,
          updatedAt: now,
        };
        saveSessionToLocal(newSession);
        setSessionsData((prev) => new Map(prev).set(sessionIdFromUrl, newSession));
        setTabs((prev) => {
          if (prev.some((t) => t.id === sessionIdFromUrl)) return prev;
          return [...prev, { id: sessionIdFromUrl, title: "New chat", createdAt: now }];
        });
        setActiveSessionId(sessionIdFromUrl);
      }
      initialLoadDone.current = true;
    };

    load();
  }, [sessionIdFromUrl]);

  // Sync URL when activeSessionId changes (user-initiated)
  const updateUrl = useCallback(
    (sessionId: string | null) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (sessionId) {
            next.set(SEARCH_PARAM, sessionId);
          } else {
            next.delete(SEARCH_PARAM);
          }
          return next;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  const createSession = useCallback((): string => {
    const id = generateSessionId();
    const now = Date.now();
    const newSession: AiChatSessionData = {
      id,
      messages: [],
      pinnedFindings: [],
      selectedMessageId: null,
      createdAt: now,
      updatedAt: now,
    };
    saveSessionToLocal(newSession);
    setSessionsData((prev) => new Map(prev).set(id, newSession));
    setTabs((prev) => [...prev, { id, title: "New chat", createdAt: now }]);
    setActiveSessionId(id);
    updateUrl(id);
    return id;
  }, [updateUrl]);

  const switchSession = useCallback(
    (sessionId: string) => {
      setActiveSessionId(sessionId);
      updateUrl(sessionId);
    },
    [updateUrl]
  );

  const closeSession = useCallback(
    (sessionId: string) => {
      setTabs((prev) => {
        const remaining = prev.filter((t) => t.id !== sessionId);
        if (activeSessionId === sessionId) {
          const nextId = remaining.length > 0 ? remaining[0].id : null;
          setActiveSessionId(nextId);
          updateUrl(nextId);
        }
        return remaining;
      });
      setSessionsData((prev) => {
        const next = new Map(prev);
        next.delete(sessionId);
        return next;
      });
      deleteSessionFromLocal(sessionId);
    },
    [activeSessionId, updateUrl]
  );

  const persistSession = useCallback(
    (data: Omit<AiChatSessionData, "id" | "createdAt" | "updatedAt">) => {
      const sid = activeSessionId;
      if (!sid) return;

      const existing = sessionsData.get(sid);
      const now = Date.now();
      const full: AiChatSessionData = {
        ...data,
        id: sid,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        title: data.title ?? getTabTitle(data.messages),
      };
      saveSessionToLocal(full);
      setSessionsData((prev) => new Map(prev).set(sid, full));
      setTabs((prev) =>
        prev.map((t) => (t.id === sid ? { ...t, title: full.title ?? t.title } : t))
      );
      // Optionally save to API for sharing (debounced in real impl)
      saveSessionToApi(full).catch(() => {});
    },
    [activeSessionId, sessionsData]
  );

  const getSessionData = useCallback(
    (sessionId: string): AiChatSessionData | null => {
      return sessionsData.get(sessionId) ?? null;
    },
    [sessionsData]
  );

  // If no session in URL and no tabs, create one on first interaction
  useEffect(() => {
    if (!initialLoadDone.current) return;
    if (!sessionIdFromUrl && tabs.length === 0) {
      createSession();
    }
  }, [sessionIdFromUrl, tabs.length, createSession]);

  const activeSession = activeSessionId ? sessionsData.get(activeSessionId) ?? null : null;

  return {
    tabs,
    activeSessionId,
    activeSession,
    isLoadingShared,
    createSession,
    switchSession,
    closeSession,
    persistSession,
    getSessionData,
  };
}
