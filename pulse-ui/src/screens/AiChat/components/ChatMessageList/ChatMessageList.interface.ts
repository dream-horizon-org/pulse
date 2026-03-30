import { ChatMessage } from "../../types/chat";

export interface ChatMessageListProps {
  messages: ChatMessage[];
  onSelectSuggestion: (query: string) => void;
}
