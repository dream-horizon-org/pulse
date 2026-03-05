import { useEffect, useRef } from "react";
import { ScrollArea, Stack } from "@mantine/core";
import { ChatMessage } from "../ChatMessage";
import { EmptyState } from "../EmptyState";
import { ChatMessageListProps } from "./ChatMessageList.interface";
import classes from "./ChatMessageList.module.css";

export const ChatMessageList = ({
  messages,
  onSelectSuggestion,
}: ChatMessageListProps) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  if (messages.length === 0) {
    return <EmptyState onSelectSuggestion={onSelectSuggestion} />;
  }

  return (
    <ScrollArea className={classes.container} type="auto">
      <Stack className={classes.messages}>
        {messages.map((msg) => (
          <ChatMessage key={msg.id} message={msg} />
        ))}
        <div ref={bottomRef} />
      </Stack>
    </ScrollArea>
  );
};
