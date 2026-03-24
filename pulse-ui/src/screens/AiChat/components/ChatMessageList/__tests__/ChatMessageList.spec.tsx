import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { ChatMessageList } from "../ChatMessageList";
import { mockMessages } from "../../../__mocks__/chatMocks";
import { AI_CHAT_TEXTS, SUGGESTED_QUERIES } from "../../../AiChat.constants";

describe("ChatMessageList", () => {
  const mockOnSelectSuggestion = jest.fn();

  beforeEach(() => {
    mockOnSelectSuggestion.mockClear();
  });

  it("renders messages", () => {
    renderWithProviders(
      <ChatMessageList
        messages={mockMessages}
        onSelectSuggestion={mockOnSelectSuggestion}
      />,
    );

    expect(screen.getByText("Top 5 screens by load time")).toBeInTheDocument();
    expect(
      screen.getByText(/Here are the top 5 screens by load time/),
    ).toBeInTheDocument();
  });

  it("shows empty state when no messages", () => {
    renderWithProviders(
      <ChatMessageList
        messages={[]}
        onSelectSuggestion={mockOnSelectSuggestion}
      />,
    );

    expect(screen.getByText(AI_CHAT_TEXTS.WELCOME_TITLE)).toBeInTheDocument();
    expect(
      screen.getByText(AI_CHAT_TEXTS.WELCOME_SUBTITLE),
    ).toBeInTheDocument();
  });

  it("renders suggestion chips in empty state", () => {
    renderWithProviders(
      <ChatMessageList
        messages={[]}
        onSelectSuggestion={mockOnSelectSuggestion}
      />,
    );

    const firstSuggestion = SUGGESTED_QUERIES[0];
    const suggestionButton = screen.getByRole("button", {
      name: firstSuggestion,
    });
    expect(suggestionButton).toBeInTheDocument();

    userEvent.click(suggestionButton);
    expect(mockOnSelectSuggestion).toHaveBeenCalledWith(firstSuggestion);
  });
});
