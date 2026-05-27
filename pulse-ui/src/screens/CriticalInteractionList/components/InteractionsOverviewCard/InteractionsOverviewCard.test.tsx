import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { InteractionsOverviewCard } from "./InteractionsOverviewCard";

const mockMutate = jest.fn();

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mockUseMutation: any = {
  mutate: mockMutate,
  isPending: false,
  isSuccess: false,
  isError: false,
  data: undefined,
};

jest.mock("../../../../contexts", () => ({
  useProjectContext: () => ({ projectId: "test-project" }),
}));

jest.mock(
  "../../../../hooks/useGetInteractionsOverview/useGetInteractionsOverview",
  () => ({
    useGetInteractionsOverview: () => mockUseMutation,
  }),
);

const renderComponent = (props: { interactionNames?: string[] } = {}) =>
  render(
    <MantineProvider>
      <InteractionsOverviewCard {...props} />
    </MantineProvider>,
  );

describe("InteractionsOverviewCard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseMutation.isPending = false;
    mockUseMutation.isSuccess = false;
    mockUseMutation.isError = false;
    mockUseMutation.data = undefined;
  });

  it("renders skeleton loading state while isPending is true and does not render summary text", () => {
    mockUseMutation.isPending = true;

    renderComponent();

    // Should not show the executive summary heading
    expect(screen.queryByText("Executive summary")).not.toBeInTheDocument();
    // Should not show Regenerate button
    expect(
      screen.queryByRole("button", { name: /regenerate insights/i }),
    ).not.toBeInTheDocument();
  });

  it("renders summary text, Report as of timestamp, and Regenerate button on success", () => {
    mockUseMutation.isSuccess = true;
    mockUseMutation.data = {
      data: {
        summary: "All critical interactions are performing within thresholds.",
        cached: true,
        cachedAt: "2026-05-22T10:30:00Z",
      },
      error: null,
      status: 200,
    };

    renderComponent();

    expect(
      screen.getByText(
        "All critical interactions are performing within thresholds.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Report as of/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /regenerate insights/i }),
    ).toBeInTheDocument();
  });

  it("calls mutate with { regenerate: true } when Regenerate insights button is clicked", () => {
    mockUseMutation.isSuccess = true;
    mockUseMutation.data = {
      data: {
        summary: "Summary text",
        cached: true,
        cachedAt: "2026-05-22T10:30:00Z",
      },
      error: null,
      status: 200,
    };

    renderComponent();

    const btn = screen.getByRole("button", { name: /regenerate insights/i });
    fireEvent.click(btn);

    expect(mockMutate).toHaveBeenCalledWith({ regenerate: true });
  });

  it("renders interaction name as an anchor link when interactionNames is provided", () => {
    mockUseMutation.isSuccess = true;
    mockUseMutation.data = {
      data: {
        summary: "ContestJoin is critically broken and needs immediate attention.",
        cached: false,
        cachedAt: "2026-05-22T10:30:00Z",
      },
      error: null,
      status: 200,
    };

    renderComponent({ interactionNames: ["ContestJoin"] });

    const link = screen.getByRole("link", { name: "ContestJoin" });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute(
      "href",
      expect.stringContaining("ContestJoin"),
    );
  });

  it("renders inline error message on error and does not crash", () => {
    mockUseMutation.isError = true;

    renderComponent();

    expect(
      screen.getByText(/unable to load overview/i),
    ).toBeInTheDocument();
    // Page does not crash — no summary shown
    expect(screen.queryByText("Executive summary")).not.toBeInTheDocument();
  });
});
