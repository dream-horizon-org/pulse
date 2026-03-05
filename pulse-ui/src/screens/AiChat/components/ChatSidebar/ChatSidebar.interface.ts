import { ChatSession } from "../../types/chat";

export interface ChatSidebarProps {
  sessions: ChatSession[];
  activeSessionId: string | null;
  isLoading?: boolean;
  onNewChat: () => void;
  onSelectSession: (sessionId: string) => void;
}
