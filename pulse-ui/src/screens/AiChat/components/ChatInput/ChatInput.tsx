import { useState, useRef, KeyboardEvent } from "react";
import { Textarea, ActionIcon, Flex } from "@mantine/core";
import { IconSend } from "@tabler/icons-react";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";
import { ChatInputProps } from "./ChatInput.interface";
import classes from "./ChatInput.module.css";

export const ChatInput = ({ onSend, isStreaming }: ChatInputProps) => {
  const [value, setValue] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed || isStreaming) return;
    onSend(trimmed);
    setValue("");
    textareaRef.current?.focus();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const canSend = value.trim().length > 0 && !isStreaming;

  return (
    <Flex className={classes.container}>
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => setValue(e.currentTarget.value)}
        onKeyDown={handleKeyDown}
        placeholder={AI_CHAT_TEXTS.PLACEHOLDER}
        autosize
        minRows={1}
        maxRows={6}
        className={classes.input}
        disabled={isStreaming}
      />
      <ActionIcon
        size="lg"
        variant="filled"
        color="teal"
        onClick={handleSend}
        disabled={!canSend}
        loading={isStreaming}
        aria-label="Send message"
        className={classes.sendButton}
      >
        <IconSend size={16} />
      </ActionIcon>
    </Flex>
  );
};
