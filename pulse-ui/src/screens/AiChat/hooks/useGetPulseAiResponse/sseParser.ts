import { StreamingCallbacks } from "./useGetPulseAiResponse.interface";

interface SsePayload {
  type?: string;
  content?: string;
  blocks?: Array<Record<string, unknown>>;
  message?: string;
}

export function handleSseLine(
  line: string,
  callbacks: StreamingCallbacks,
): "done" | "continue" {
  const trimmed = line.trim();
  if (!trimmed.startsWith("data: ")) return "continue";

  const payload = trimmed.slice(6);

  if (payload === "[DONE]") {
    callbacks.onComplete();
    return "done";
  }

  try {
    const parsed: SsePayload = JSON.parse(payload);

    if (parsed.type === "text" && parsed.content) {
      callbacks.onToken(parsed.content);
    } else if (
      parsed.type === "content_blocks" &&
      Array.isArray(parsed.blocks)
    ) {
      const charts = parsed.blocks
        .filter((b) => b.block_type === "chart")
        .map(({ block_type, ...rest }) => rest);
      const tables = parsed.blocks
        .filter((b) => b.block_type === "table")
        .map(({ block_type, ...rest }) => rest);
      if (charts.length) callbacks.onCharts(charts);
      if (tables.length) callbacks.onTables(tables);
      callbacks.onContentBlocks?.(parsed.blocks);
    } else if (parsed.type === "error") {
      callbacks.onError(parsed.message || "Unknown agent error");
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
