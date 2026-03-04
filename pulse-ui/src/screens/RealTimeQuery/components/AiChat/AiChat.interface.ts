import { QueryResult } from "../../RealTimeQuery.interface";
import { AiInsights } from "../../../../hooks/useAiQuery";

/**
 * A single pinnable finding from an AI response.
 * Users pin individual key points, not entire messages.
 */
export interface PinnedFinding {
  /** Unique ID for this finding */
  id: string;
  /** The key point text */
  text: string;
  /** Which assistant message this came from */
  sourceMessageId: string;
}

/**
 * A single message in the AI chat thread
 */
export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  /** User's question or AI answer text */
  content: string;
  timestamp: number;
  // Only for assistant messages:
  insights?: AiInsights;
  result?: QueryResult;
  sourcesAnalyzed?: string[];
  timeRange?: { start: string; end: string };
  isLoading?: boolean;
}

/**
 * Props for the ChatThread (left panel)
 */
export interface ChatThreadProps {
  messages: ChatMessage[];
  pinnedFindings: PinnedFinding[];
  selectedMessageId: string | null;
  onSelectMessage: (id: string) => void;
  onSendMessage: (text: string) => void;
  onUnpinFinding: (findingId: string) => void;
  isLoading: boolean;
}

/**
 * Props for a single chat message bubble
 */
export interface ChatMessageBubbleProps {
  message: ChatMessage;
  isSelected: boolean;
  onSelect: (id: string) => void;
}

/**
 * Props for the ResponseDetail (right panel)
 */
export interface ResponseDetailProps {
  message: ChatMessage | null;
  pinnedFindings: PinnedFinding[];
  onPinFinding: (messageId: string, keyPointIndex: number) => void;
  onUnpinFinding: (findingId: string) => void;
}

/**
 * Props for the AiChat container component
 */
export interface AiChatProps {
  // No props needed — the AiChat container uses its own hook internally
}

