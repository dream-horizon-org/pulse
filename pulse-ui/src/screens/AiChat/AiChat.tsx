import { useEffect, useMemo } from "react";
import { Alert, Box, LoadingOverlay } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useChatStore } from "../../stores/useChatStore";
import { useAiChatHydration } from "./hooks/useAiChatHydration";
import { useHandleSend } from "./hooks/useHandleSend";
import { ChatSidebar } from "./components/ChatSidebar";
import { ChatMessageList } from "./components/ChatMessageList";
import { ChatInput } from "./components/ChatInput";
import { AI_CHAT_TEXTS } from "./AiChat.constants";
import "./AiChat.vars.css";
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

  const {
    handleNewChat,
    isLoadingSessions,
    isCreatingSession,
    sessionsErrorMessage,
    onRetrySessions,
  } = useAiChatHydration();
  const { handleSend, cancel } = useHandleSend();

  useEffect(() => () => cancel(), [cancel]);

  const activeMessages = useMemo(
    () => (activeSessionId ? (messages[activeSessionId] ?? []) : []),
    [activeSessionId, messages],
  );

  const errorDisplay = useMemo(
    () =>
      error === AI_CHAT_TEXTS.SESSION_HISTORY_LOAD_FAILED
        ? AI_CHAT_TEXTS.ERROR_GENERIC
        : error,
    [error],
  );

  return (
    <Box className={classes.container}>
      <ChatSidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        isLoading={isLoadingSessions}
        isCreatingSession={isCreatingSession}
        sessionsError={sessionsErrorMessage}
        onRetrySessions={onRetrySessions}
        onNewChat={handleNewChat}
        onSelectSession={switchSession}
      />
      <Box className={classes.chatArea}>
        <LoadingOverlay
          visible={isCreatingSession}
          overlayProps={{ blur: 2 }}
          zIndex={50}
        />
        {errorDisplay && (
          <Alert
            icon={<IconAlertCircle size={16} />}
            color="red"
            variant="light"
            withCloseButton
            onClose={() => setError(null)}
            className={classes.errorBar}
          >
            {errorDisplay}
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
