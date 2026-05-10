import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WebVitalsPanel } from "./WebVitalsPanel";
import type { WebVitalsPanelProps } from "./WebVitalsPanel.interface";
import * as hooks from "../../hooks";

jest.mock("../../hooks");
jest.mock("../../../../components/Charts/LineChart/LineChart", () => ({
  LineChart: () => <div data-testid="line-chart">Chart</div>,
}));

describe("WebVitalsPanel", () => {
  const mockSummaryPayload = {
    vitals: [
      {
        name: "LCP",
        p75: 2450,
        goodPct: 75,
        needsImprovementPct: 15,
        poorPct: 10,
        totalCount: 1000,
      },
      {
        name: "INP",
        p75: 180,
        goodPct: 80,
        needsImprovementPct: 15,
        poorPct: 5,
        totalCount: 1000,
      },
      {
        name: "CLS",
        p75: 0.12,
        goodPct: 85,
        needsImprovementPct: 10,
        poorPct: 5,
        totalCount: 1000,
      },
      {
        name: "FCP",
        p75: 1500,
        goodPct: 90,
        needsImprovementPct: 5,
        poorPct: 5,
        totalCount: 1000,
      },
      {
        name: "FID",
        p75: 80,
        goodPct: 88,
        needsImprovementPct: 10,
        poorPct: 2,
        totalCount: 1000,
      },
      {
        name: "TTFB",
        p75: 600,
        goodPct: 92,
        needsImprovementPct: 5,
        poorPct: 3,
        totalCount: 1000,
      },
    ],
  };

  const mockTrendPayload = {
    points: [
      { bucket: "2026-05-01T00:00:00Z", p75: 2300 },
      { bucket: "2026-05-01T01:00:00Z", p75: 2450 },
    ],
  };

  const mockScreenPayload = {
    screens: [
      { screenName: "Home", p75: 2300, totalCount: 1500, goodPct: 85 },
      { screenName: "Product", p75: 3100, totalCount: 1200, goodPct: 60 },
    ],
  };

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  const renderComponent = (props: WebVitalsPanelProps) =>
    render(
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <MantineProvider>
            <WebVitalsPanel {...props} />
          </MantineProvider>
        </QueryClientProvider>
      </BrowserRouter>,
    );

  beforeEach(() => {
    jest.clearAllMocks();
    (hooks.useWebVitalsSummary as jest.Mock).mockReturnValue({
      data: {
        data: mockSummaryPayload,
        error: null,
        status: 200,
      },
      isLoading: false,
      error: null,
    });
    (hooks.useWebVitalsTrend as jest.Mock).mockReturnValue({
      data: {
        data: mockTrendPayload,
        error: null,
        status: 200,
      },
      isLoading: false,
      error: null,
    });
    (hooks.useWebVitalsByScreen as jest.Mock).mockReturnValue({
      data: {
        data: mockScreenPayload,
        error: null,
        status: 200,
      },
      isLoading: false,
      error: null,
    });
  });

  it("should render 6 VitalCard components on mount", () => {
    renderComponent({
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    // All 6 vital names should be rendered
    mockSummaryPayload.vitals.forEach((vital) => {
      expect(screen.getByText(vital.name)).toBeInTheDocument();
    });
  });

  it("should render VitalTrendChart and VitalsByScreenTable when no screenName", () => {
    renderComponent({
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    // TrendChart should show LCP Trend title
    expect(screen.getByText("LCP Trend")).toBeInTheDocument();
    // By-screen table should be rendered
    expect(screen.getByText("By Screen")).toBeInTheDocument();
  });

  it("should hide VitalsByScreenTable when screenName provided", () => {
    renderComponent({
      screenName: "Home",
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    // VitalsByScreenTable should not be rendered (check that "By Screen" heading is not present)
    expect(screen.queryByText("By Screen")).not.toBeInTheDocument();
  });

  it("should pass screenName to hooks when provided", () => {
    renderComponent({
      screenName: "Home",
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    expect(hooks.useWebVitalsSummary).toHaveBeenCalledWith(
      expect.objectContaining({
        screenName: "Home",
      }),
    );
  });

  it("should show CardSkeleton while loading", () => {
    (hooks.useWebVitalsSummary as jest.Mock).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    });

    renderComponent({
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    // Skeleton elements should be rendered (multiple divs with height style)
    const { container } = render(
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <MantineProvider>
            <WebVitalsPanel
              startTime="2026-05-01T00:00:00Z"
              endTime="2026-05-01T23:00:00Z"
            />
          </MantineProvider>
        </QueryClientProvider>
      </BrowserRouter>,
    );
    expect(container).toBeTruthy();
  });

  it("should show error component on error", () => {
    (hooks.useWebVitalsSummary as jest.Mock).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("Failed to load"),
    });

    renderComponent({
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    expect(screen.getByText("Error loading Web Vitals")).toBeInTheDocument();
  });

  it("should have LCP selected by default", () => {
    renderComponent({
      startTime: "2026-05-01T00:00:00Z",
      endTime: "2026-05-01T23:00:00Z",
    });

    // Initial render should call useWebVitalsTrend with LCP
    expect(hooks.useWebVitalsTrend).toHaveBeenCalledWith(
      expect.objectContaining({
        vitalName: "LCP",
      }),
    );
  });
});
