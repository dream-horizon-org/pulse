export interface AiSessionMessage {
  role: "user" | "model";
  text: string;
  charts: Array<{
    type: string;
    title: string;
    data: Record<string, unknown>;
    description?: string;
  }>;
  tables: Array<{
    title: string;
    columns: Array<{ key: string; label: string; type?: string }>;
    rows: Array<Record<string, unknown>>;
    description?: string;
  }>;
}

export interface AiSessionDetail {
  id: string;
  user_id: string;
  messages: AiSessionMessage[];
  last_update_time: number;
}
