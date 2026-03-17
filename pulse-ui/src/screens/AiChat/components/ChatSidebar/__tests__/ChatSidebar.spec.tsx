import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { ChatSidebar } from "../ChatSidebar";
import { mockSessions } from "../../../__mocks__/chatMocks";
import { AI_CHAT_TEXTS } from "../../../AiChat.constants";

describe("ChatSidebar", () => {
  const mockOnNewChat = jest.fn();
  const mockOnSelectSession = jest.fn();

  beforeEach(() => {
    mockOnNewChat.mockClear();
    mockOnSelectSession.mockClear();
  });

  it("renders New Chat button", () => {
    renderWithProviders(
      <ChatSidebar
        sessions={[]}
        activeSessionId={null}
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    expect(
      screen.getByRole("button", { name: AI_CHAT_TEXTS.NEW_CHAT }),
    ).toBeInTheDocument();
  });

  it("renders session list", () => {
    renderWithProviders(
      <ChatSidebar
        sessions={mockSessions}
        activeSessionId={null}
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    expect(screen.getByText("Screen load times")).toBeInTheDocument();
    expect(screen.getByText("Crash analysis")).toBeInTheDocument();
  });

  it("clicking session triggers onSelectSession", () => {
    renderWithProviders(
      <ChatSidebar
        sessions={mockSessions}
        activeSessionId={null}
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    userEvent.click(screen.getByText("Screen load times"));
    expect(mockOnSelectSession).toHaveBeenCalledWith("s1");
  });

  it("clicking New Chat triggers onNewChat", () => {
    renderWithProviders(
      <ChatSidebar
        sessions={mockSessions}
        activeSessionId={null}
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    userEvent.click(
      screen.getByRole("button", { name: AI_CHAT_TEXTS.NEW_CHAT }),
    );
    expect(mockOnNewChat).toHaveBeenCalled();
  });
});
