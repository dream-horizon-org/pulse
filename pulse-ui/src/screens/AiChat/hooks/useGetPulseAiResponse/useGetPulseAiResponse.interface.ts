import { AiChartConfig, AiTableConfig } from "../../types/chat";

export interface ContentBlock {
  block_type: "chart" | "table" | string;
  [key: string]: unknown;
}

export interface StreamingCallbacks {
  onToken: (token: string) => void;
  onCharts: (charts: AiChartConfig[]) => void;
  onTables: (tables: AiTableConfig[]) => void;
  onContentBlocks?: (blocks: ContentBlock[]) => void;
  onComplete: () => void;
  onError: (message: string) => void;
}

export interface UseGetPulseAiResponseReturn {
  sendMessage: (
    sessionId: string,
    text: string,
    callbacks: StreamingCallbacks,
  ) => void;
  cancel: () => void;
}
