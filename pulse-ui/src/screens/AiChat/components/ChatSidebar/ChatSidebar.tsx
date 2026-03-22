import {
  Button,
  Loader,
  NavLink,
  ScrollArea,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconPlus, IconMessage } from "@tabler/icons-react";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";
import { ChatSidebarProps } from "./ChatSidebar.interface";
import classes from "./ChatSidebar.module.css";

export const ChatSidebar = ({
  sessions,
  activeSessionId,
  isLoading,
  isCreatingSession = false,
  sessionsError = null,
  onRetrySessions,
  onNewChat,
  onSelectSession,
}: ChatSidebarProps) => {
  if (!sessions) return null;

  return (
    <Stack className={classes.container}>
      <div className={classes.header}>
        <Title order={5} className={classes.sessionsTitle}>
          {AI_CHAT_TEXTS.SESSIONS_TITLE}
        </Title>
        <Button
          leftSection={<IconPlus size={14} />}
          size="xs"
          variant="light"
          color="teal"
          onClick={() => void onNewChat()}
          disabled={isCreatingSession}
        >
          {AI_CHAT_TEXTS.NEW_CHAT}
        </Button>
      </div>
      <ScrollArea className={classes.list} type="auto">
        {sessionsError ? (
          <Stack gap="xs" className={classes.loadingState}>
            <Text size="sm" c="red">
              {sessionsError}
            </Text>
            {onRetrySessions ? (
              <Button
                size="xs"
                variant="light"
                color="teal"
                onClick={onRetrySessions}
              >
                {AI_CHAT_TEXTS.RETRY}
              </Button>
            ) : null}
          </Stack>
        ) : isLoading ? (
          <Stack className={classes.loadingState}>
            <Loader size="sm" color="teal" />
            <Text size="xs" c="dimmed">
              {AI_CHAT_TEXTS.LOADING_SESSIONS}
            </Text>
          </Stack>
        ) : sessions.length === 0 ? (
          <Text size="sm" c="dimmed" className={classes.emptyText}>
            {AI_CHAT_TEXTS.NO_SESSIONS}
          </Text>
        ) : (
          sessions.map((session) => (
            <NavLink
              key={session.id}
              label={session.title || AI_CHAT_TEXTS.NEW_CONVERSATION}
              description={session.lastMessage}
              classNames={{
                label: classes.ellipsisText,
                description: classes.ellipsisText,
              }}
              leftSection={<IconMessage size={16} />}
              active={session.id === activeSessionId}
              onClick={() => onSelectSession(session.id)}
              className={classes.sessionItem}
              color="teal"
            />
          ))
        )}
      </ScrollArea>
    </Stack>
  );
};
