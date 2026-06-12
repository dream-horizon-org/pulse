import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import HeroMetrics from "../HeroMetrics";
import {
  mockMetricGroups,
  mockMetricGroupsNoErrors,
  mockMetricGroupsHighErrors,
} from "../__mock__/HeroMetrics.mock";

const renderWithProvider = (component: React.ReactElement) => {
  return render(<MantineProvider>{component}</MantineProvider>);
};

describe("HeroMetrics", () => {
  it("renders without crashing", () => {
    renderWithProvider(<HeroMetrics />);
  });

  it("renders all metric groups with default mock data", () => {
    renderWithProvider(<HeroMetrics />);

    expect(screen.getByText("Stability")).toBeInTheDocument();
    expect(screen.getByText("Network Health")).toBeInTheDocument();
    expect(screen.getByText("Performance")).toBeInTheDocument();
  });

  it("renders provided metric groups", () => {
    renderWithProvider(<HeroMetrics metricGroups={mockMetricGroups} />);

    // Check if all metrics are rendered
    expect(screen.getByText("Crashes")).toBeInTheDocument();
    expect(screen.getByText("ANRs")).toBeInTheDocument();
    expect(screen.getByText("Timeouts")).toBeInTheDocument();
    expect(screen.getByText("HTTP 4xx")).toBeInTheDocument();
    expect(screen.getByText("HTTP 5xx")).toBeInTheDocument();
  });

  it("renders no errors state correctly", () => {
    renderWithProvider(<HeroMetrics metricGroups={mockMetricGroupsNoErrors} />);

    // All values should be 0
    const values = screen.getAllByText("0");
    expect(values.length).toBeGreaterThan(0);
  });

  it("renders high errors state correctly", () => {
    renderWithProvider(
      <HeroMetrics metricGroups={mockMetricGroupsHighErrors} />,
    );

    // Check for high error values
    expect(screen.getByText("245")).toBeInTheDocument(); // Crashes
    expect(screen.getByText("892")).toBeInTheDocument(); // HTTP 4xx
  });

  it("renders metric group descriptions", () => {
    renderWithProvider(<HeroMetrics metricGroups={mockMetricGroups} />);

    expect(
      screen.getByText("Critical errors that impact user experience"),
    ).toBeInTheDocument();
    expect(screen.getByText("API and connectivity issues")).toBeInTheDocument();
    expect(
      screen.getByText("UI rendering and responsiveness"),
    ).toBeInTheDocument();
  });
});
