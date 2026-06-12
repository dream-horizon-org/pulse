import {
  Box,
  Text,
  Textarea,
  ActionIcon,
  Badge,
  Collapse,
  Group,
  ScrollArea,
} from "@mantine/core";
import {
  IconSend,
  IconPinFilled,
  IconX,
  IconChevronDown,
  IconChevronUp,
  IconSparkles,
} from "@tabler/icons-react";
import { useState, useCallback, useRef, useEffect, KeyboardEvent } from "react";
import { ChatThreadProps } from "./AiChat.interface";
import { ChatMessageBubble } from "./ChatMessageBubble";
import classes from "./AiChat.module.css";

const SUGGESTION_QUERIES = [
  "Why did the conversion drop?",
  "What are the top interactions with problems?",
  "Did this user perform purchase_complete in the last 24 hours?",
  "Show me error events from the last hour",
];

export function ChatThread({
  messages,
  pinnedFindings,
  selectedMessageId,
  onSelectMessage,
  onSendMessage,
  onUnpinFinding,
  isLoading,
}: ChatThreadProps) {
  const [inputValue, setInputValue] = useState("");
  const [pinnedOpen, setPinnedOpen] = useState(true);
  const scrollViewportRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (scrollViewportRef.current) {
      scrollViewportRef.current.scrollTo({
        top: scrollViewportRef.current.scrollHeight,
        behavior: "smooth",
      });
    }
  }, [messages.length]);

  const handleSend = useCallback(() => {
    if (!inputValue.trim() || isLoading) return;
    onSendMessage(inputValue.trim());
    setInputValue("");
  }, [inputValue, isLoading, onSendMessage]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend]
  );

  const handleSuggestionClick = useCallback(
    (suggestion: string) => {
      onSendMessage(suggestion);
    },
    [onSendMessage]
  );

  return (
    <Box className={classes.chatThread}>
      {/* Pinned Findings Banner */}
      {pinnedFindings.length > 0 && (
        <Box className={classes.pinnedBanner}>
          <Group
            justify="space-between"
            className={classes.pinnedHeader}
            onClick={() => setPinnedOpen(!pinnedOpen)}
            style={{ cursor: "pointer" }}
          >
            <Group gap="xs">
              <IconPinFilled size={14} color="var(--mantine-color-teal-6)" />
              <Text size="xs" fw={600} c="dimmed" tt="uppercase">
                Research Notes
              </Text>
              <Badge size="xs" variant="light" color="teal">
                {pinnedFindings.length}
              </Badge>
            </Group>
            {pinnedOpen ? (
              <IconChevronUp size={14} color="var(--mantine-color-gray-5)" />
            ) : (
              <IconChevronDown size={14} color="var(--mantine-color-gray-5)" />
            )}
          </Group>
          <Collapse in={pinnedOpen}>
            <Box className={classes.pinnedList}>
              {pinnedFindings.map((finding) => (
                <Box key={finding.id} className={classes.pinnedItem}>
                  <Text size="xs" className={classes.pinnedText}>
                    {finding.text}
                  </Text>
                  <ActionIcon
                    size="xs"
                    variant="subtle"
                    color="gray"
                    onClick={() => onUnpinFinding(finding.id)}
                    className={classes.pinnedRemove}
                  >
                    <IconX size={12} />
                  </ActionIcon>
                </Box>
              ))}
            </Box>
          </Collapse>
        </Box>
      )}

      {/* Messages scroll area */}
      <ScrollArea
        className={classes.messagesArea}
        viewportRef={scrollViewportRef}
        type="auto"
        offsetScrollbars
      >
        {messages.length === 0 ? (
          <Box className={classes.emptyState}>
            <IconSparkles
              size={32}
              color="var(--mantine-color-teal-4)"
              style={{ opacity: 0.6 }}
            />
            <Text size="sm" c="dimmed" ta="center" mt="sm">
              Ask a question about your data
            </Text>
            <Box className={classes.suggestionsGrid}>
              {SUGGESTION_QUERIES.map((suggestion) => (
                <Box
                  key={suggestion}
                  className={classes.suggestionChip}
                  onClick={() => handleSuggestionClick(suggestion)}
                >
                  {suggestion}
                </Box>
              ))}
            </Box>
          </Box>
        ) : (
          <Box className={classes.messagesList}>
            {messages.map((msg) => (
              <ChatMessageBubble
                key={msg.id}
                message={msg}
                isSelected={msg.id === selectedMessageId}
                onSelect={onSelectMessage}
              />
            ))}
          </Box>
        )}
      </ScrollArea>

      {/* Input area */}
      <Box className={classes.inputArea}>
        <Textarea
          className={classes.chatInput}
          placeholder="Ask a follow-up question..."
          value={inputValue}
          onChange={(e) => setInputValue(e.currentTarget.value)}
          onKeyDown={handleKeyDown}
          minRows={1}
          maxRows={4}
          autosize
          disabled={isLoading}
          rightSection={
            <ActionIcon
              size="sm"
              variant="filled"
              color="teal"
              onClick={handleSend}
              disabled={!inputValue.trim() || isLoading}
              className={classes.sendButton}
            >
              <IconSend size={14} />
            </ActionIcon>
          }
          rightSectionWidth={36}
        />
      </Box>
    </Box>
  );
}

