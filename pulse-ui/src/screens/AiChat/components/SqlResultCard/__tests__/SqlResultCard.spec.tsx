import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../../../../test-utils/renderWithProviders";
import { SqlResultCard } from "../SqlResultCard";

describe("SqlResultCard", () => {
  it("renders SQL in code block", () => {
    const sql = "SELECT * FROM otel_traces LIMIT 10";
    renderWithProviders(<SqlResultCard sql={sql} />);

    expect(screen.getByText("Generated SQL")).toBeInTheDocument();
    expect(screen.getByText(sql)).toBeInTheDocument();
  });

  it("shows copy button", () => {
    const sql = "SELECT 1";
    renderWithProviders(<SqlResultCard sql={sql} />);

    const copyButton = screen.getByRole("button");
    expect(copyButton).toBeInTheDocument();
  });

  it("renders nothing for empty SQL", () => {
    renderWithProviders(<SqlResultCard sql="" />);

    expect(screen.queryByText("Generated SQL")).not.toBeInTheDocument();
  });
});
