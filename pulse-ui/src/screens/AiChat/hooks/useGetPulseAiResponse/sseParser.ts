import { AiChartConfig, AiTableConfig } from "../../types/chat";
import {
  ContentBlock,
  StreamingCallbacks,
} from "./useGetPulseAiResponse.interface";
import { AI_CHAT_TEXTS, SSE_CONSTANTS } from "../../AiChat.constants";

export type SseHandleResult = "done" | "continue";

interface SsePayload {
  type?: string;
  content?: string;
  blocks?: ContentBlock[];
  message?: string;
  user_event_id?: string;
  assistant_event_id?: string;
  invocation_id?: string;
}

export function handleSseLine(
  line: string,
  callbacks: StreamingCallbacks,
): SseHandleResult {
  const trimmed = line.trim();
  if (!trimmed.startsWith(SSE_CONSTANTS.DATA_PREFIX)) return "continue";

  const payload = trimmed.slice(SSE_CONSTANTS.DATA_PREFIX.length);

  if (payload === SSE_CONSTANTS.DONE_MARKER) {
    callbacks.onComplete();
    return "done";
  }

  try {
    const parsed: SsePayload = JSON.parse(payload);

    if (parsed.type === SSE_CONSTANTS.EVENT_TYPE.META) {
      const userEventId =
        typeof parsed.user_event_id === "string"
          ? parsed.user_event_id
          : undefined;
      const assistantEventId =
        typeof parsed.assistant_event_id === "string"
          ? parsed.assistant_event_id
          : undefined;
      const invocationId =
        typeof parsed.invocation_id === "string"
          ? parsed.invocation_id
          : undefined;
      if (userEventId || assistantEventId || invocationId) {
        callbacks.onMeta?.({
          userEventId,
          assistantEventId,
          invocationId,
        });
      }
    } else if (
      parsed.type === SSE_CONSTANTS.EVENT_TYPE.TEXT &&
      parsed.content
    ) {
      callbacks.onToken(parsed.content);
    } else if (
      parsed.type === SSE_CONSTANTS.EVENT_TYPE.CONTENT_BLOCKS &&
      Array.isArray(parsed.blocks)
    ) {
      const charts = parsed.blocks
        .filter((b) => b.block_type === SSE_CONSTANTS.BLOCK_TYPE.CHART)
        .map(({ block_type, ...rest }) => rest as unknown as AiChartConfig);
      const tables = parsed.blocks
        .filter((b) => b.block_type === SSE_CONSTANTS.BLOCK_TYPE.TABLE)
        .map(({ block_type, ...rest }) => rest as unknown as AiTableConfig);
      if (charts.length) callbacks.onCharts(charts);
      if (tables.length) callbacks.onTables(tables);
      callbacks.onContentBlocks?.(parsed.blocks);
    } else if (parsed.type === SSE_CONSTANTS.EVENT_TYPE.ERROR) {
      callbacks.onError(parsed.message || AI_CHAT_TEXTS.UNKNOWN_AGENT_ERROR);
    }
  } catch {
    // Non-JSON SSE line, skip
  }

  return "continue";
}

export async function readSseStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  callbacks: StreamingCallbacks,
): Promise<void> {
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const line of lines) {
      if (handleSseLine(line, callbacks) === "done") return;
    }
  }

  callbacks.onComplete();
}
