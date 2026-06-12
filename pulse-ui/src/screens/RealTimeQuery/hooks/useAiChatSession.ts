import { useState, useCallback, useRef, useEffect } from "react";
import { useAiQueryExecution } from "./useAiQueryExecution";
import { ChatMessage, PinnedFinding } from "../components/AiChat/AiChat.interface";
import { QueryResult } from "../RealTimeQuery.interface";
import { AiInsights } from "../../../hooks/useAiQuery";
import { AiChatSessionData } from "../services/aiChatSessionStorage";

interface UseAiChatSessionOptions {
  sessionId: string | null;
  initialSession: AiChatSessionData | null;
  onPersist?: (data: Omit<AiChatSessionData, "id" | "createdAt" | "updatedAt">) => void;
}

interface UseAiChatSessionReturn {
  messages: ChatMessage[];
  selectedMessageId: string | null;
  pinnedFindings: PinnedFinding[];
  isLoading: boolean;
  sendMessage: (text: string) => void;
  selectMessage: (id: string) => void;
  pinFinding: (messageId: string, keyPointIndex: number) => void;
  unpinFinding: (findingId: string) => void;
}

let nextId = 1;
function generateId(prefix: string): string {
  return `${prefix}_${Date.now()}_${nextId++}`;
}

function getTabTitle(messages: ChatMessage[]): string {
  const firstUser = messages.find((m) => m.role === "user");
  if (firstUser?.content) {
    const text = firstUser.content.trim();
    return text.length > 30 ? text.slice(0, 27) + "..." : text;
  }
  return "New chat";
}

/**
 * Hook that manages the full chat session state:
 * - message history
 * - pinned findings (individual key points from responses)
 * - selected message for the right panel
 * - sends queries via useAiQueryExecution, passing pinned context
 * - persists via onPersist when state changes
 */
export function useAiChatSession(options: UseAiChatSessionOptions): UseAiChatSessionReturn {
  const { sessionId, initialSession, onPersist } = options;

  const [messages, setMessages] = useState<ChatMessage[]>(() => initialSession?.messages ?? []);
  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(
    () => initialSession?.selectedMessageId ?? null
  );
  const [pinnedFindings, setPinnedFindings] = useState<PinnedFinding[]>(
    () => initialSession?.pinnedFindings ?? []
  );

  // Reset state when switching sessions
  useEffect(() => {
    if (initialSession) {
      setMessages(initialSession.messages);
      setSelectedMessageId(initialSession.selectedMessageId);
      setPinnedFindings(initialSession.pinnedFindings);
    } else {
      setMessages([]);
      setSelectedMessageId(null);
      setPinnedFindings([]);
    }
  }, [sessionId, initialSession]);

  // Track the loading assistant message id so we can update it on completion
  const loadingMessageIdRef = useRef<string | null>(null);

  // Build pinned context string
  const getPinnedContext = useCallback((): string | undefined => {
    if (pinnedFindings.length === 0) return undefined;
    return pinnedFindings.map((f) => f.text).join("; ");
  }, [pinnedFindings]);

  const pinnedContextRef = useRef<string | undefined>(undefined);
  // Keep ref in sync
  pinnedContextRef.current = getPinnedContext();

  const {
    executeAiQuery,
    isLoading,
  } = useAiQueryExecution({
    onSuccess: (
      queryResult: QueryResult,
      respInsights?: AiInsights | null,
      respSources?: string[] | null,
      respTimeRange?: { start: string; end: string } | null,
    ) => {
      const assistantId = loadingMessageIdRef.current;
      if (!assistantId) return;

      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantId
            ? {
                ...msg,
                content: respInsights?.answer || "Query completed.",
                insights: respInsights || undefined,
                result: queryResult,
                sourcesAnalyzed: respSources || undefined,
                timeRange: respTimeRange || undefined,
                isLoading: false,
              }
            : msg
        )
      );

      // Auto-select the completed message
      setSelectedMessageId(assistantId);
      loadingMessageIdRef.current = null;
    },
    onError: (error: string) => {
      const assistantId = loadingMessageIdRef.current;
      if (!assistantId) return;

      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantId
            ? {
                ...msg,
                content: `Error: ${error}`,
                isLoading: false,
              }
            : msg
        )
      );
      loadingMessageIdRef.current = null;
    },
  });

  const sendMessage = useCallback(
    (text: string) => {
      if (!text.trim() || isLoading) return;

      const userMsg: ChatMessage = {
        id: generateId("user"),
        role: "user",
        content: text.trim(),
        timestamp: Date.now(),
      };

      const assistantMsg: ChatMessage = {
        id: generateId("assistant"),
        role: "assistant",
        content: "",
        timestamp: Date.now(),
        isLoading: true,
      };

      loadingMessageIdRef.current = assistantMsg.id;

      setMessages((prev) => [...prev, userMsg, assistantMsg]);
      setSelectedMessageId(assistantMsg.id);

      // Fire query with pinned context
      executeAiQuery(text.trim(), pinnedContextRef.current);
    },
    [isLoading, executeAiQuery]
  );

  const selectMessage = useCallback((id: string) => {
    setSelectedMessageId(id);
  }, []);

  const pinFinding = useCallback(
    (messageId: string, keyPointIndex: number) => {
      const msg = messages.find((m) => m.id === messageId);
      const keyPoint = msg?.insights?.keyPoints?.[keyPointIndex];
      if (!keyPoint) return;
      const text = keyPoint.text;

      setPinnedFindings((prevPins) => {
        const alreadyPinned = prevPins.some(
          (p) => p.sourceMessageId === messageId && p.text === text
        );
        if (alreadyPinned) return prevPins;

        return [
          ...prevPins,
          {
            id: generateId("pin"),
            text,
            sourceMessageId: messageId,
          },
        ];
      });
    },
    [messages]
  );

  const unpinFinding = useCallback((findingId: string) => {
    setPinnedFindings((prev) => prev.filter((p) => p.id !== findingId));
  }, []);

  // Persist when state changes (debounced)
  const persistRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!sessionId || !onPersist) return;
    if (persistRef.current) clearTimeout(persistRef.current);
    persistRef.current = setTimeout(() => {
      onPersist({
        messages,
        pinnedFindings,
        selectedMessageId,
        title: getTabTitle(messages),
      });
      persistRef.current = null;
    }, 500);
    return () => {
      if (persistRef.current) clearTimeout(persistRef.current);
    };
  }, [sessionId, onPersist, messages, pinnedFindings, selectedMessageId]);

  return {
    messages,
    selectedMessageId,
    pinnedFindings,
    isLoading,
    sendMessage,
    selectMessage,
    pinFinding,
    unpinFinding,
  };
}

