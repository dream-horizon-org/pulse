import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { SuggestedInteractionCard } from "../SuggestedInteractionCard";
import {
  mockSuggestion,
  mockSuggestionLargeNumbers,
  mockSuggestionLongDurations,
  mockSuggestionSmallNumbers,
  mockSuggestionSingleEvent,
  mockSuggestionThreeEvents,
  mockSuggestionSpecialChars,
} from "../__mock__/SuggestedInteractionCard.mock";

describe("SuggestedInteractionCard", () => {
  const defaultProps = {
    suggestion: mockSuggestion,
    onDismiss: jest.fn(),
    onActivate: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders the pattern label in PascalCase with To separator", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(
        screen.getByTitle("GoShoppingToTelescopeSelected"),
      ).toBeInTheDocument();
    });

    it("renders all event pills in the pattern flow", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("Go shopping")).toBeInTheDocument();
      expect(screen.getByText("Telescope selected")).toBeInTheDocument();
    });

    it("renders PascalCase name for a single event without To separator", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionSingleEvent}
        />,
      );
      expect(
        screen.getByTitle("CheckoutCompleted"),
      ).toBeInTheDocument();
    });

    it("renders PascalCase name with To between three events", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionThreeEvents}
        />,
      );
      expect(
        screen.getByTitle("AddToCartToGoShoppingToCheckoutCompleted"),
      ).toBeInTheDocument();
    });

    it("handles special characters in event names for PascalCase conversion", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionSpecialChars}
        />,
      );
      expect(
        screen.getByTitle("UserLoginSuccessToDashboardView"),
      ).toBeInTheDocument();
    });

    it("renders arrow separators between event pills", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("→", { exact: false })).toBeInTheDocument();
    });

    it("renders only one event pill when suggestion has single event", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionSingleEvent}
        />,
      );
      expect(screen.getByText("Checkout completed")).toBeInTheDocument();
      expect(screen.queryByText("→", { exact: false })).not.toBeInTheDocument();
    });

    it("does not show the Suggested badge", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.queryByText("Suggested")).not.toBeInTheDocument();
    });

    it("renders all metric labels", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("Volume")).toBeInTheDocument();
      expect(screen.getByText("Sessions")).toBeInTheDocument();
      expect(screen.getByText("P50")).toBeInTheDocument();
      expect(screen.getByText("P95")).toBeInTheDocument();
      expect(screen.getByText("Consistency")).toBeInTheDocument();
    });
  });

  describe("metric values with default mock", () => {
    it("displays correct Volume value", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("8.4K")).toBeInTheDocument();
    });

    it("displays correct Sessions percentage", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("72.5%")).toBeInTheDocument();
    });

    it("displays correct P50 value in milliseconds", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("680ms")).toBeInTheDocument();
    });

    it("displays correct P95 value in seconds", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      expect(screen.getByText("2.10s")).toBeInTheDocument();
    });

    it("displays correct Consistency value", () => {
      renderWithProviders(<SuggestedInteractionCard {...defaultProps} />);
      // cv = 0.12, consistency = (1 - 0.12) * 100 = 88%
      expect(screen.getByText("88%")).toBeInTheDocument();
    });
  });

  describe("metric formatting", () => {
    it("formats count in millions with M suffix", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionLargeNumbers}
        />,
      );
      expect(screen.getByText("2.5M")).toBeInTheDocument();
    });

    it("formats count less than 1000 as raw number", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionSmallNumbers}
        />,
      );
      expect(screen.getByText("500")).toBeInTheDocument();
    });

    it("formats duration >= 1s as seconds", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          suggestion={mockSuggestionLongDurations}
        />,
      );
      // medianSpanS = 3.456 -> "3.46s"
      expect(screen.getByText("3.46s")).toBeInTheDocument();
    });
  });

  describe("actions", () => {
    it("calls onDismiss with suggestion id when Dismiss is clicked", () => {
      const onDismiss = jest.fn();
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          onDismiss={onDismiss}
        />,
      );

      userEvent.click(screen.getByText("Dismiss"));
      expect(onDismiss).toHaveBeenCalledWith(1);
      expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it("calls onActivate with the full suggestion when Track this is clicked", () => {
      const onActivate = jest.fn();
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          onActivate={onActivate}
        />,
      );

      userEvent.click(screen.getByText("Track this"));
      expect(onActivate).toHaveBeenCalledWith(mockSuggestion);
      expect(onActivate).toHaveBeenCalledTimes(1);
    });
  });

  describe("loading states", () => {
    it("disables Dismiss button when isDismissing is true", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          isDismissing={true}
        />,
      );

      const button = screen.getByText("Dismiss").closest("button");
      expect(button).toBeDisabled();
    });

    it("disables Track this button when isActivating is true", () => {
      renderWithProviders(
        <SuggestedInteractionCard
          {...defaultProps}
          isActivating={true}
        />,
      );

      const button = screen.getByText("Track this").closest("button");
      expect(button).toBeDisabled();
    });
  });
});
