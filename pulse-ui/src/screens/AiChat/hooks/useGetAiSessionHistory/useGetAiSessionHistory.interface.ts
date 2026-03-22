import { ChatRole } from "../../types/chat";

export interface AiSessionMessage {
  /** ADK persisted event id when provided by pulse_ai (session history). */
  id?: string;
  invocation_id?: string;
  role: ChatRole;
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
