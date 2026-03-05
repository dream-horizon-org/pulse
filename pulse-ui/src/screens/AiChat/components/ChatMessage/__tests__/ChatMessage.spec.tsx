import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { ChatMessage } from "../ChatMessage";
import {
  mockMessages,
  mockMessageWithSql,
  mockStreamingMessage,
} from "../../../__tests__/__mocks__/chatMocks";

describe("ChatMessage", () => {
  it("renders user message text", () => {
    renderWithProviders(<ChatMessage message={mockMessages[0]} />);

    expect(screen.getByText("Top 5 screens by load time")).toBeInTheDocument();
  });

  it("renders AI message text", () => {
    renderWithProviders(<ChatMessage message={mockMessages[1]} />);

    expect(
      screen.getByText(/Here are the top 5 screens by load time/),
    ).toBeInTheDocument();
    expect(screen.getByText(/HomeScreen - 2.5s/)).toBeInTheDocument();
  });

  it("renders SqlResultCard when message contains SQL code block", () => {
    renderWithProviders(<ChatMessage message={mockMessageWithSql} />);

    expect(
      screen.getByText(
        /SELECT ScreenName, avg\(Duration\/1e6\) as avg_load_ms FROM otel_traces LIMIT 5/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Generated SQL")).toBeInTheDocument();
  });

  it("shows typing indicator when isStreaming and no text", () => {
    const { container } = renderWithProviders(
      <ChatMessage message={mockStreamingMessage} />,
    );

    // TypingIndicator replaces markdown/SQL when streaming with no text
    expect(screen.queryByText("Generated SQL")).not.toBeInTheDocument();
    // Message bubble should render (contains TypingIndicator with 3 dots)
    const bubbles = container.querySelectorAll('[class*="bubble"]');
    expect(bubbles.length).toBeGreaterThan(0);
  });
});
