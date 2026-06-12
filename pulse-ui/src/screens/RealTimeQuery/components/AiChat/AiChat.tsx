import { Box, Loader, Center, Text } from "@mantine/core";
import { useMemo } from "react";
import { useAiChatSessionManager } from "../../hooks/useAiChatSessionManager";
import { useAiChatSession } from "../../hooks/useAiChatSession";
import { AiChatTabs } from "./AiChatTabs";
import { ChatThread } from "./ChatThread";
import { ResponseDetail } from "./ResponseDetail";
import classes from "./AiChat.module.css";

/**
 * Split-panel AI chat interface with session tabs and URL sync.
 *
 * - Chrome-like tabs for multiple chat sessions
 * - Session ID in URL (?s=xxx) for sharing
 * - Left (~45%): ChatThread — pinned findings, messages, input
 * - Right (~55%): ResponseDetail — answer, key points, sources, raw data
 */
export function AiChat() {
  const {
    tabs,
    activeSessionId,
    activeSession,
    isLoadingShared,
    createSession,
    switchSession,
    closeSession,
    persistSession,
  } = useAiChatSessionManager();

  const {
    messages,
    selectedMessageId,
    pinnedFindings,
    isLoading,
    sendMessage,
    selectMessage,
    pinFinding,
    unpinFinding,
  } = useAiChatSession({
    sessionId: activeSessionId,
    initialSession: activeSession,
    onPersist: persistSession,
  });

  const selectedMessage = useMemo(() => {
    if (!selectedMessageId) return null;
    return messages.find((m) => m.id === selectedMessageId) || null;
  }, [messages, selectedMessageId]);

  if (isLoadingShared) {
    return (
      <Box className={classes.container}>
        <Center h="100%" style={{ flex: 1 }}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed" ml="md">
            Loading shared chat...
          </Text>
        </Center>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {/* Chrome-like tabs */}
      <AiChatTabs
        tabs={tabs}
        activeSessionId={activeSessionId}
        onSwitch={switchSession}
        onClose={closeSession}
        onNew={createSession}
      />

      {/* Chat content */}
      <Box className={classes.chatContent}>
        <ChatThread
          messages={messages}
          pinnedFindings={pinnedFindings}
          selectedMessageId={selectedMessageId}
          onSelectMessage={selectMessage}
          onSendMessage={sendMessage}
          onUnpinFinding={unpinFinding}
          isLoading={isLoading}
        />

        <Box className={classes.detailPanel}>
          <ResponseDetail
            message={selectedMessage}
            pinnedFindings={pinnedFindings}
            onPinFinding={pinFinding}
            onUnpinFinding={unpinFinding}
          />
        </Box>
      </Box>
    </Box>
  );
}

