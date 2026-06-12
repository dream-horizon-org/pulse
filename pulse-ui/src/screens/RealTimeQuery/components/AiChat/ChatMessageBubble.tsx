import { Box, Text } from "@mantine/core";
import { IconUser, IconSparkles } from "@tabler/icons-react";
import { ChatMessageBubbleProps } from "./AiChat.interface";
import { AiLoadingAnimation } from "./AiLoadingAnimation";
import classes from "./AiChat.module.css";

export function ChatMessageBubble({
  message,
  isSelected,
  onSelect,
}: ChatMessageBubbleProps) {
  const isUser = message.role === "user";

  const handleClick = () => {
    if (!isUser && !message.isLoading) {
      onSelect(message.id);
    }
  };

  return (
    <Box
      className={`${classes.messageBubble} ${isUser ? classes.userBubble : classes.assistantBubble} ${isSelected ? classes.selectedBubble : ""}`}
      onClick={handleClick}
      data-role={message.role}
      data-selected={isSelected || undefined}
    >
      <Box className={classes.messageAvatar}>
        {isUser ? (
          <IconUser size={14} />
        ) : (
          <IconSparkles size={14} />
        )}
      </Box>

      <Box className={classes.messageContent}>
        {message.isLoading ? (
          <AiLoadingAnimation />
        ) : (
          <>
            <Text size="sm" lineClamp={isUser ? undefined : 3}>
              {isUser
                ? message.content
                : message.insights?.answer || message.content}
            </Text>
            {!isUser && message.insights && (
              <Text size="xs" c="dimmed" mt={4}>
                {message.insights.keyPoints.length} key finding
                {message.insights.keyPoints.length !== 1 ? "s" : ""} · Click to
                view details
              </Text>
            )}
          </>
        )}
      </Box>
    </Box>
  );
}

