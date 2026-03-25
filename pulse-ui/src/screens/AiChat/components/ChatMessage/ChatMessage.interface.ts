import { ChatMessage } from "../../types/chat";

export interface ChatMessageProps {
  /** When missing (e.g. bad list data), renders nothing. */
  message?: ChatMessage | null;
}
