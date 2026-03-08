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
import { AI_CHAT_TEXTS, AI_CHAT_LIMITS } from "../../AiChat.constants";
import { ChatSidebarProps } from "./ChatSidebar.interface";
import { truncate } from "./ChatSidebar.utils";
import classes from "./ChatSidebar.module.css";

export const ChatSidebar = ({
  sessions,
  activeSessionId,
  isLoading,
  onNewChat,
  onSelectSession,
}: ChatSidebarProps) => (
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
        onClick={onNewChat}
      >
        {AI_CHAT_TEXTS.NEW_CHAT}
      </Button>
    </div>
    <ScrollArea className={classes.list} type="auto">
      {isLoading ? (
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
            label={truncate(
              session.title || AI_CHAT_TEXTS.NEW_CONVERSATION,
              AI_CHAT_LIMITS.SIDEBAR_TITLE_TRUNCATE,
            )}
            description={
              session.lastMessage
                ? truncate(
                    session.lastMessage,
                    AI_CHAT_LIMITS.SIDEBAR_DESC_TRUNCATE,
                  )
                : undefined
            }
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
