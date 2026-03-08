import { create } from "zustand";
import { devtools } from "zustand/middleware";
import {
  AiChartConfig,
  AiTableConfig,
  ChatMessage,
  ChatSession,
} from "../types/chat";

interface ChatState {
  sessions: ChatSession[];
  activeSessionId: string | null;
  messages: Record<string, ChatMessage[]>;
  isStreaming: boolean;
  error: string | null;
}

interface ChatActions {
  createSession: (session: ChatSession) => void;
  deleteSession: (sessionId: string) => void;
  switchSession: (sessionId: string) => void;
  setSessions: (sessions: ChatSession[]) => void;
  addMessage: (sessionId: string, message: ChatMessage) => void;
  updateLastMessage: (sessionId: string, text: string) => void;
  updateLastMessageCharts: (sessionId: string, charts: AiChartConfig[]) => void;
  updateLastMessageTables: (sessionId: string, tables: AiTableConfig[]) => void;
  appendToLastMessage: (sessionId: string, token: string) => void;
  markLastMessageComplete: (sessionId: string) => void;
  markLastMessageError: (sessionId: string, fallbackText: string) => void;
  updateSessionTitle: (sessionId: string, title: string) => void;
  setMessages: (sessionId: string, messages: ChatMessage[]) => void;
  setStreaming: (isStreaming: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

const initialState: ChatState = {
  sessions: [],
  activeSessionId: null,
  messages: {},
  isStreaming: false,
  error: null,
};

export const useChatStore = create<ChatState & ChatActions>()(
  devtools(
    (set, get) => ({
      ...initialState,

      createSession: (session) =>
        set((state) => ({
          sessions: [session, ...state.sessions],
          activeSessionId: session.id,
          messages: { ...state.messages, [session.id]: [] },
        })),

      deleteSession: (sessionId) =>
        set((state) => {
          const { [sessionId]: _, ...remaining } = state.messages;
          const filteredSessions = state.sessions.filter(
            (s) => s.id !== sessionId,
          );
          return {
            sessions: filteredSessions,
            messages: remaining,
            activeSessionId:
              state.activeSessionId === sessionId
                ? (filteredSessions[0]?.id ?? null)
                : state.activeSessionId,
          };
        }),

      switchSession: (sessionId) => set({ activeSessionId: sessionId }),

      setSessions: (sessions) => set({ sessions }),

      addMessage: (sessionId, message) =>
        set((state) => {
          const existing = state.messages[sessionId] ?? [];
          const updatedSessions = state.sessions.map((s) =>
            s.id === sessionId
              ? {
                  ...s,
                  lastMessage: message.text,
                  updatedAt: message.timestamp,
                }
              : s,
          );
          return {
            messages: {
              ...state.messages,
              [sessionId]: [...existing, message],
            },
            sessions: updatedSessions,
          };
        }),

      updateLastMessage: (sessionId, text) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          const last = updated[updated.length - 1];
          updated[updated.length - 1] = { ...last, text };
          return {
            messages: { ...state.messages, [sessionId]: updated },
          };
        }),

      updateLastMessageCharts: (sessionId, charts) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          const last = updated[updated.length - 1];
          updated[updated.length - 1] = { ...last, charts };
          return {
            messages: { ...state.messages, [sessionId]: updated },
          };
        }),

      updateLastMessageTables: (sessionId, tables) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          const last = updated[updated.length - 1];
          updated[updated.length - 1] = { ...last, tables };
          return {
            messages: { ...state.messages, [sessionId]: updated },
          };
        }),

      appendToLastMessage: (sessionId, token) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          const last = updated[updated.length - 1];
          if (last.role !== "model") return state;
          updated[updated.length - 1] = { ...last, text: last.text + token };
          return { messages: { ...state.messages, [sessionId]: updated } };
        }),

      markLastMessageComplete: (sessionId) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          updated[updated.length - 1] = {
            ...updated[updated.length - 1],
            isStreaming: false,
          };
          return { messages: { ...state.messages, [sessionId]: updated } };
        }),

      markLastMessageError: (sessionId, fallbackText) =>
        set((state) => {
          const sessionMessages = state.messages[sessionId] ?? [];
          if (sessionMessages.length === 0) return state;
          const updated = [...sessionMessages];
          const last = updated[updated.length - 1];
          updated[updated.length - 1] = {
            ...last,
            isStreaming: false,
            text: last.text || fallbackText,
          };
          return { messages: { ...state.messages, [sessionId]: updated } };
        }),

      updateSessionTitle: (sessionId, title) =>
        set((state) => ({
          sessions: state.sessions.map((s) =>
            s.id === sessionId ? { ...s, title } : s,
          ),
        })),

      setMessages: (sessionId, messages) =>
        set((state) => ({
          messages: { ...state.messages, [sessionId]: messages },
        })),

      setStreaming: (isStreaming) => set({ isStreaming }),

      setError: (error) => set({ error }),

      reset: () => set(initialState),
    }),
    { name: "chat-store" },
  ),
);
