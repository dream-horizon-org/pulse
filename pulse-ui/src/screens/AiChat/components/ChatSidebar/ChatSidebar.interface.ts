import { ChatSession } from "../../types/chat";

export interface ChatSidebarProps {
  sessions: ChatSession[];
  activeSessionId: string | null;
  isLoading?: boolean;
  isCreatingSession?: boolean;
  sessionsError?: string | null;
  onRetrySessions?: () => void;
  onNewChat: () => void | Promise<void>;
  onSelectSession: (sessionId: string) => void;
}
