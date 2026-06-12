import { Box, Text, Avatar } from "@mantine/core";
import { IconUser, IconSparkles } from "@tabler/icons-react";
import { MarkdownContent } from "../../../../components/MarkdownContent";
import { SqlResultCard } from "../SqlResultCard";
import { AiChartCard } from "../AiChartCard";
import { AiTableCard } from "../AiTableCard";
import { TypingIndicator } from "../TypingIndicator";
import { ChatMessageProps } from "./ChatMessage.interface";
import { extractSql, stripSqlBlocks } from "./ChatMessage.utils";
import classes from "./ChatMessage.module.css";

export const ChatMessage = ({ message }: ChatMessageProps) => {
  if (!message) return null;

  const isUser = message.role === "user";
  const sql = message.sql ?? extractSql(message.text);
  const displayText = sql ? stripSqlBlocks(message.text) : message.text;
  /** Hide structured cards until streaming ends so typewriter text is not visually preceded by chart/table UI (see useHandleSend deferred flush). */
  const showStructuredCards = !message.isStreaming;

  return (
    <Box
      className={`${classes.row} ${isUser ? classes.userRow : classes.aiRow}`}
    >
      {!isUser && (
        <Avatar
          size="sm"
          radius="xl"
          color="teal"
          variant="light"
          className={classes.avatar}
        >
          <IconSparkles size={14} />
        </Avatar>
      )}
      <Box
        className={`${classes.bubble} ${isUser ? classes.userBubble : classes.aiBubble}`}
      >
        {message.isStreaming && !message.text ? (
          <TypingIndicator />
        ) : (
          <>
            <MarkdownContent
              content={displayText}
              className={classes.markdown}
            />
            {showStructuredCards && sql && <SqlResultCard sql={sql} />}
            {showStructuredCards &&
              message.charts
                ?.filter(
                  (chart): chart is NonNullable<typeof chart> => chart != null,
                )
                .map((chart, idx) => (
                  <AiChartCard
                    key={`chart-${chart.title}-${idx}`}
                    chart={chart}
                  />
                ))}
            {showStructuredCards &&
              message.tables
                ?.filter(
                  (table): table is NonNullable<typeof table> => table != null,
                )
                .map((table, idx) => (
                  <AiTableCard
                    key={`table-${table.title}-${idx}`}
                    table={table}
                  />
                ))}
          </>
        )}
        <Text size="xs" c="dimmed" className={classes.timestamp}>
          {new Date(message.timestamp).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </Text>
      </Box>
      {isUser && (
        <Avatar
          size="sm"
          radius="xl"
          color="teal"
          variant="filled"
          className={classes.avatar}
        >
          <IconUser size={14} />
        </Avatar>
      )}
    </Box>
  );
};
