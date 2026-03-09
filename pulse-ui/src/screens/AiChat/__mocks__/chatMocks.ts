import { ChatMessage, ChatSession } from "../types/chat";

export const mockSessions: ChatSession[] = [
  {
    id: "s1",
    title: "Screen load times",
    createdAt: 1709000000,
    updatedAt: 1709000100,
  },
  {
    id: "s2",
    title: "Crash analysis",
    createdAt: 1709000200,
    updatedAt: 1709000300,
  },
];

export const mockMessages: ChatMessage[] = [
  {
    id: "m1",
    role: "user",
    text: "Top 5 screens by load time",
    timestamp: 1709000050,
  },
  {
    id: "m2",
    role: "model",
    text: "Here are the top 5 screens by load time:\n\n1. HomeScreen - 2.5s\n2. ProfileScreen - 1.8s",
    timestamp: 1709000060,
  },
];

export const mockMessageWithSql: ChatMessage = {
  id: "m3",
  role: "model",
  text: "Here is the query:\n```sql\nSELECT ScreenName, avg(Duration/1e6) as avg_load_ms FROM otel_traces LIMIT 5\n```",
  timestamp: 1709000070,
};

export const mockStreamingMessage: ChatMessage = {
  id: "m4",
  role: "model",
  text: "",
  timestamp: 1709000080,
  isStreaming: true,
};
