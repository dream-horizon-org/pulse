import { AI_CHAT_TEXTS } from "./AiChat.constants";

export function toMs(ts: number): number {
  if (!ts) return Date.now();
  return ts < 1e12 ? ts * 1000 : ts;
}

/** Known safe strings passed to {@link useChatStore.setError}; anything else is treated as server/proxy leakage. */
const ALLOWED_CHAT_BANNER_ERRORS = new Set([
  AI_CHAT_TEXTS.ERROR_GENERIC,
  AI_CHAT_TEXTS.SESSION_HISTORY_LOAD_FAILED,
  AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED,
  AI_CHAT_TEXTS.SESSION_CREATE_FAILED,
]);

/**
 * Maps chat banner `error` to user-safe copy. Logs and replaces unknown strings (e.g. upstream Gemini errors).
 */
export function sanitizeChatErrorForDisplay(
  error: string | null,
): string | null {
  if (error == null || error === "") return null;
  if (!ALLOWED_CHAT_BANNER_ERRORS.has(error)) {
    console.warn(
      "[Pulse AI] Non-allowlisted error banner (showing generic):",
      error,
    );
    return AI_CHAT_TEXTS.ERROR_GENERIC;
  }
  if (error === AI_CHAT_TEXTS.SESSION_HISTORY_LOAD_FAILED) {
    return AI_CHAT_TEXTS.ERROR_GENERIC;
  }
  return error;
}
