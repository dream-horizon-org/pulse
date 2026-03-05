import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { EmptyState } from "../EmptyState";
import { AI_CHAT_TEXTS, SUGGESTED_QUERIES } from "../../../AiChat.constants";

describe("EmptyState", () => {
  const mockOnSelectSuggestion = jest.fn();

  beforeEach(() => {
    mockOnSelectSuggestion.mockClear();
  });

  it("renders welcome message", () => {
    renderWithProviders(
      <EmptyState onSelectSuggestion={mockOnSelectSuggestion} />,
    );

    expect(screen.getByText(AI_CHAT_TEXTS.WELCOME_TITLE)).toBeInTheDocument();
    expect(screen.getByText(AI_CHAT_TEXTS.WELCOME_SUBTITLE)).toBeInTheDocument();
  });

  it("renders suggested query buttons", () => {
    renderWithProviders(
      <EmptyState onSelectSuggestion={mockOnSelectSuggestion} />,
    );

    SUGGESTED_QUERIES.forEach((query) => {
      expect(
        screen.getByRole("button", { name: query }),
      ).toBeInTheDocument();
    });
  });

  it("clicking suggestion triggers onSelectSuggestion", () => {
    renderWithProviders(
      <EmptyState onSelectSuggestion={mockOnSelectSuggestion} />,
    );

    const firstSuggestion = SUGGESTED_QUERIES[0];
    userEvent.click(
      screen.getByRole("button", { name: firstSuggestion }),
    );

    expect(mockOnSelectSuggestion).toHaveBeenCalledWith(firstSuggestion);
  });
});
