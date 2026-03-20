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

  it("disables New Chat while a session is being created", () => {
    renderWithProviders(
      <ChatSidebar
        sessions={mockSessions}
        activeSessionId={null}
        isCreatingSession
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    expect(
      screen.getByRole("button", { name: AI_CHAT_TEXTS.NEW_CHAT }),
    ).toBeDisabled();
  });

  it("shows retry when sessions failed to load", () => {
    const onRetry = jest.fn();
    renderWithProviders(
      <ChatSidebar
        sessions={[]}
        activeSessionId={null}
        sessionsError={AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED}
        onRetrySessions={onRetry}
        onNewChat={mockOnNewChat}
        onSelectSession={mockOnSelectSession}
      />,
    );

    expect(
      screen.getByText(AI_CHAT_TEXTS.SESSIONS_LOAD_FAILED),
    ).toBeInTheDocument();
    userEvent.click(screen.getByRole("button", { name: AI_CHAT_TEXTS.RETRY }));
    expect(onRetry).toHaveBeenCalled();
  });
});
