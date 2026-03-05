import { Button, Loader, NavLink, ScrollArea, Stack, Text, Title } from "@mantine/core";
import { IconPlus, IconMessage } from "@tabler/icons-react";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";
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
      <Title order={5} className={classes.sessionsTitle}>{AI_CHAT_TEXTS.SESSIONS_TITLE}</Title>
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
          <Text size="xs" c="dimmed">Loading sessions...</Text>
        </Stack>
      ) : sessions.length === 0 ? (
        <Text size="sm" c="dimmed" className={classes.emptyText}>
          {AI_CHAT_TEXTS.NO_SESSIONS}
        </Text>
      ) : (
        sessions.map((session) => (
          <NavLink
            key={session.id}
            label={truncate(session.title || "New conversation", 30)}
            description={
              session.lastMessage
                ? truncate(session.lastMessage, 40)
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
