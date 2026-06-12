/**
 * AI Chat Session Storage
 * Persists chat sessions for URL sharing and restore.
 * Uses localStorage for same-device; API (or mock) for cross-device sharing.
 */

import { ChatMessage, PinnedFinding } from "../components/AiChat/AiChat.interface";
import { v4 as uuidV4 } from "uuid";
import { API_BASE_URL } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";

const STORAGE_KEY_PREFIX = "pulse-ai-session-";
const SESSION_IDS_KEY = "pulse-ai-session-ids";

export interface AiChatSessionData {
  id: string;
  messages: ChatMessage[];
  pinnedFindings: PinnedFinding[];
  selectedMessageId: string | null;
  createdAt: number;
  updatedAt: number;
  /** First user message, used as tab title */
  title?: string;
}

/** Generate a short session ID for URLs */
export function generateSessionId(): string {
  return uuidV4().replace(/-/g, "").slice(0, 12);
}

/** Save session to localStorage */
export function saveSessionToLocal(session: AiChatSessionData): void {
  try {
    const key = `${STORAGE_KEY_PREFIX}${session.id}`;
    localStorage.setItem(key, JSON.stringify(session));
    const ids = getSessionIdsFromLocal();
    if (!ids.includes(session.id)) {
      ids.push(session.id);
      localStorage.setItem(SESSION_IDS_KEY, JSON.stringify(ids));
    }
  } catch (e) {
    console.warn("[AiChatSession] Failed to save to localStorage:", e);
  }
}

/** Load session from localStorage */
export function loadSessionFromLocal(sessionId: string): AiChatSessionData | null {
  try {
    const key = `${STORAGE_KEY_PREFIX}${sessionId}`;
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    return JSON.parse(raw) as AiChatSessionData;
  } catch (e) {
    console.warn("[AiChatSession] Failed to load from localStorage:", e);
    return null;
  }
}

/** Get list of session IDs stored locally */
export function getSessionIdsFromLocal(): string[] {
  try {
    const raw = localStorage.getItem(SESSION_IDS_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as string[];
  } catch {
    return [];
  }
}

/** Delete session from localStorage */
export function deleteSessionFromLocal(sessionId: string): void {
  try {
    localStorage.removeItem(`${STORAGE_KEY_PREFIX}${sessionId}`);
    const ids = getSessionIdsFromLocal().filter((id) => id !== sessionId);
    localStorage.setItem(SESSION_IDS_KEY, JSON.stringify(ids));
  } catch (e) {
    console.warn("[AiChatSession] Failed to delete from localStorage:", e);
  }
}

/** API path for session fetch/save (for cross-device sharing) */
export const AI_SESSION_API_PATH = "/query/ai/session";

/** Fetch session from API (for shared URLs) - goes through makeRequest so mock works */
export async function fetchSessionFromApi(sessionId: string): Promise<AiChatSessionData | null> {
  try {
    const res = await makeRequest<AiChatSessionData>({
      url: `${API_BASE_URL}${AI_SESSION_API_PATH}/${sessionId}`,
      init: { method: "GET" },
    });
    if (res.error || !res.data) return null;
    return res.data;
  } catch (e) {
    console.warn("[AiChatSession] Failed to fetch session from API:", e);
    return null;
  }
}

/** Save session to API (for sharing) */
export async function saveSessionToApi(session: AiChatSessionData): Promise<boolean> {
  try {
    const res = await makeRequest<{ ok: boolean; id: string }>({
      url: `${API_BASE_URL}${AI_SESSION_API_PATH}`,
      init: {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(session),
      },
    });
    return !res.error && !!res.data?.ok;
  } catch (e) {
    console.warn("[AiChatSession] Failed to save session to API:", e);
    return false;
  }
}
