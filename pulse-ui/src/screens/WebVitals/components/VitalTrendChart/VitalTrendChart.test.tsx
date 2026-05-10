import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { BrowserRouter } from "react-router-dom";
import { VitalTrendChart } from "./VitalTrendChart";

describe("VitalTrendChart", () => {
  const renderComponent = (props: any) =>
    render(
      <BrowserRouter>
        <MantineProvider>
          <VitalTrendChart {...props} />
        </MantineProvider>
      </BrowserRouter>
    );

  const mockData = [
    { bucket: "2026-05-01T00:00:00Z", p75: 2300 },
    { bucket: "2026-05-01T01:00:00Z", p75: 2450 },
    { bucket: "2026-05-01T02:00:00Z", p75: 2100 },
  ];

  it("should render loading skeleton when isLoading is true", () => {
    renderComponent({
      vitalName: "LCP",
      data: undefined,
      isLoading: true,
      error: null,
    });

    // Skeleton should show title
    expect(screen.getByText("LCP Trend")).toBeInTheDocument();
  });

  it("should render error message when error is present", () => {
    renderComponent({
      vitalName: "LCP",
      data: undefined,
      isLoading: false,
      error: new Error("Failed to load data"),
    });

    expect(screen.getByText("Error loading trend data")).toBeInTheDocument();
  });

  it("should render title when data is loaded", () => {
    renderComponent({
      vitalName: "LCP",
      data: mockData,
      isLoading: false,
      error: null,
    });

    expect(screen.getByText("LCP Trend")).toBeInTheDocument();
  });

  it("should show no data message when data is empty", () => {
    renderComponent({
      vitalName: "LCP",
      data: [],
      isLoading: false,
      error: null,
    });

    expect(screen.getByText("No data available")).toBeInTheDocument();
  });
});
