import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { ChatInput } from "../ChatInput";

describe("ChatInput", () => {
  const mockOnSend = jest.fn();

  beforeEach(() => {
    mockOnSend.mockClear();
  });

  it("renders text input and send button", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    expect(
      screen.getByPlaceholderText("Ask about your app's performance..."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /send message/i })).toBeInTheDocument();
  });

  it("send button disabled when empty", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    const sendButton = screen.getByRole("button", { name: /send message/i });
    expect(sendButton).toBeDisabled();
  });

  it("send button disabled when isStreaming", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={true} />,
    );

    const sendButton = screen.getByRole("button", { name: /send message/i });
    expect(sendButton).toBeDisabled();
  });

  it("typing text enables send", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    const textarea = screen.getByPlaceholderText("Ask about your app's performance...");
    const sendButton = screen.getByRole("button", { name: /send message/i });

    expect(sendButton).toBeDisabled();
    userEvent.type(textarea, "Top 5 screens");
    expect(sendButton).not.toBeDisabled();
  });

  it("Enter triggers onSend", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    const textarea = screen.getByPlaceholderText("Ask about your app's performance...");
    userEvent.type(textarea, "Hello{Enter}");

    expect(mockOnSend).toHaveBeenCalledWith("Hello");
  });

  it("Shift+Enter does NOT trigger send", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    const textarea = screen.getByPlaceholderText("Ask about your app's performance...");
    userEvent.type(textarea, "Line 1{Shift>}{Enter}{/Shift}Line 2");

    expect(mockOnSend).not.toHaveBeenCalled();
  });

  it("input clears after send", () => {
    renderWithProviders(
      <ChatInput onSend={mockOnSend} isStreaming={false} />,
    );

    const textarea = screen.getByPlaceholderText("Ask about your app's performance...");
    userEvent.type(textarea, "Hello{Enter}");

    expect(mockOnSend).toHaveBeenCalledWith("Hello");
    expect(textarea).toHaveValue("");
  });
});
