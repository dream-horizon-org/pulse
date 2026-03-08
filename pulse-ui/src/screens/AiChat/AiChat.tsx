import { Alert, Box } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useChatStore } from "../../stores/useChatStore";
import { useAiChatHydration } from "./hooks/useAiChatHydration";
import { useHandleSend } from "./hooks/useHandleSend";
import { ChatSidebar } from "./components/ChatSidebar";
import { ChatMessageList } from "./components/ChatMessageList";
import { ChatInput } from "./components/ChatInput";
import classes from "./AiChat.module.css";

export const AiChat = () => {
  const {
    activeSessionId,
    sessions,
    messages,
    isStreaming,
    error,
    switchSession,
    setError,
  } = useChatStore();

  const { handleNewChat, isLoadingSessions } = useAiChatHydration();
  const { handleSend } = useHandleSend();

  const activeMessages = activeSessionId
    ? (messages[activeSessionId] ?? [])
    : [];

  return (
    <Box className={classes.container}>
      <ChatSidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        isLoading={isLoadingSessions}
        onNewChat={handleNewChat}
        onSelectSession={switchSession}
      />
      <Box className={classes.chatArea}>
        {error && (
          <Alert
            icon={<IconAlertCircle size={16} />}
            color="red"
            variant="light"
            withCloseButton
            onClose={() => setError(null)}
            className={classes.errorBar}
          >
            {error}
          </Alert>
        )}
        <ChatMessageList
          messages={activeMessages}
          onSelectSuggestion={handleSend}
        />
        <ChatInput onSend={handleSend} isStreaming={isStreaming} />
      </Box>
    </Box>
  );
};
