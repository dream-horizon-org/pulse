import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { InteractionReportView, ratingColor } from "../InteractionReportView";
import { mockInteractionReportV1 } from "../__mocks__/interactionReport.fixture";

const renderView = () =>
  render(
    <MantineProvider>
      <InteractionReportView report={mockInteractionReportV1} />
    </MantineProvider>,
  );

describe("InteractionReportView", () => {
  it("renders verdict rating and summary", () => {
    renderView();
    expect(screen.getByText("AMBER")).toBeInTheDocument();
    expect(
      screen.getByText(
        /Payment handshake is slow for a meaningful subset of users/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/2\. Health verdict/)).toBeInTheDocument();
  });

  it("renders segment highlights when present", () => {
    renderView();
    expect(screen.getByText(/Segment highlights/)).toBeInTheDocument();
    expect(
      screen.getAllByText(/NetworkProvider: Vi India/).length,
    ).toBeGreaterThan(0);
    expect(
      screen.getByText(/~20% of attempts; poor UX roughly 2×/),
    ).toBeInTheDocument();
  });

  it("renders prioritized actions list", () => {
    renderView();
    expect(screen.getByText(/7\. Actions/)).toBeInTheDocument();
    expect(
      screen.getByText(/Pre-warm Juspay SDK on checkout entry/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Surface retry guidance when init exceeds/),
    ).toBeInTheDocument();
    expect(screen.getAllByText("P0").length).toBeGreaterThan(0);
    expect(screen.getByText("P1")).toBeInTheDocument();
  });
});

describe("ratingColor", () => {
  it("maps health ratings to Mantine colors", () => {
    expect(ratingColor("green")).toBe("green");
    expect(ratingColor("red")).toBe("red");
    expect(ratingColor("amber")).toBe("yellow");
  });
});
