import { Box, Text, Avatar } from "@mantine/core";
import { IconUser, IconSparkles } from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ChatMessage as ChatMessageType } from "../../types/chat";
import { SqlResultCard } from "../SqlResultCard";
import { AiChartCard } from "../AiChartCard";
import { AiTableCard } from "../AiTableCard";
import { TypingIndicator } from "../TypingIndicator";
import { ChatMessageProps } from "./ChatMessage.interface";
import { extractSql, stripSqlBlocks } from "./ChatMessage.utils";
import classes from "./ChatMessage.module.css";

export const ChatMessage = ({ message }: ChatMessageProps) => {
  const isUser = message.role === "user";
  const sql = message.sql ?? extractSql(message.text);
  const displayText = sql ? stripSqlBlocks(message.text) : message.text;

  return (
    <Box className={`${classes.row} ${isUser ? classes.userRow : classes.aiRow}`}>
      {!isUser && (
        <Avatar size="sm" radius="xl" color="teal" variant="light" className={classes.avatar}>
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
            <div className={classes.markdown}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{displayText}</ReactMarkdown>
            </div>
            {sql && <SqlResultCard sql={sql} />}
            {message.charts?.map((chart, idx) => (
              <AiChartCard key={`chart-${chart.title}-${idx}`} chart={chart} />
            ))}
            {message.tables?.map((table, idx) => (
              <AiTableCard key={`table-${table.title}-${idx}`} table={table} />
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
        <Avatar size="sm" radius="xl" color="teal" variant="filled" className={classes.avatar}>
          <IconUser size={14} />
        </Avatar>
      )}
    </Box>
  );
};
