export type ChatRole = "user" | "model";

export type AiChartType = "line" | "bar" | "pie" | "area";

/** Single source for chart type literals (use instead of raw "line" | "bar" | "pie" | "area"). */
export const AI_CHART_TYPES: Record<AiChartType, AiChartType> = {
  line: "line",
  bar: "bar",
  pie: "pie",
  area: "area",
};

export type BlockType = "chart" | "table";

export type SortDir = "asc" | "desc" | null;

export interface AiChartConfig {
  type: AiChartType;
  title: string;
  data: Record<string, unknown>;
  description?: string;
}

export interface AiTableColumn {
  key: string;
  label: string;
  type?: "string" | "number";
}

export interface AiTableConfig {
  title: string;
  columns: AiTableColumn[];
  rows: Record<string, unknown>[];
  description?: string;
}

export interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
  sql?: string;
  charts?: AiChartConfig[];
  tables?: AiTableConfig[];
  timestamp: number;
  isStreaming?: boolean;
}

export interface ChatSession {
  id: string;
  title: string;
  lastMessage?: string;
  createdAt: number;
  updatedAt: number;
}

export interface AdkRunRequest {
  appName: string;
  userId: string;
  sessionId: string;
  newMessage: { role: ChatRole; parts: { text: string }[] };
  streaming?: boolean;
}

export interface AdkEvent {
  content: {
    parts: {
      text?: string;
      functionCall?: Record<string, unknown>;
      functionResponse?: Record<string, unknown>;
    }[];
    role: string;
  };
  author: string;
  id: string;
  timestamp: number;
}

export interface AdkSessionResponse {
  id: string;
  appName: string;
  userId: string;
  state: Record<string, unknown>;
  events: AdkEvent[];
  lastUpdateTime: number;
}
