import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { VitalsByScreenTable } from "./VitalsByScreenTable";
import type { VitalsByScreenTableProps } from "./VitalsByScreenTable.interface";

describe("VitalsByScreenTable", () => {
  const renderComponent = (props: VitalsByScreenTableProps) =>
    render(
      <MantineProvider>
        <VitalsByScreenTable {...props} />
      </MantineProvider>,
    );

  const mockData = [
    { screenName: "Home", p75: 2300, totalCount: 1500, goodPct: 85 },
    { screenName: "Product", p75: 3100, totalCount: 1200, goodPct: 60 },
    { screenName: "Cart", p75: 1800, totalCount: 800, goodPct: 92 },
  ];

  it("should render table with all columns", () => {
    renderComponent({
      vitalName: "LCP",
      data: mockData,
      isLoading: false,
      error: null,
    });

    expect(screen.getByText("Screen Name")).toBeInTheDocument();
    expect(screen.getByText("P75")).toBeInTheDocument();
    expect(screen.getByText("Count")).toBeInTheDocument();
    expect(screen.getByText("Good %")).toBeInTheDocument();
  });

  it("should render all data rows", () => {
    renderComponent({
      vitalName: "LCP",
      data: mockData,
      isLoading: false,
      error: null,
    });

    expect(screen.getByText("Home")).toBeInTheDocument();
    expect(screen.getByText("Product")).toBeInTheDocument();
    expect(screen.getByText("Cart")).toBeInTheDocument();
  });

  it("should render loading skeleton when isLoading is true", () => {
    renderComponent({
      vitalName: "LCP",
      data: undefined,
      isLoading: true,
      error: null,
    });

    // Skeleton should be visible
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("should render error message when error is present", () => {
    renderComponent({
      vitalName: "LCP",
      data: undefined,
      isLoading: false,
      error: new Error("Failed to load"),
    });

    expect(screen.getByText("Error loading table data")).toBeInTheDocument();
  });

  it("should format CLS P75 without ms suffix", () => {
    renderComponent({
      vitalName: "CLS",
      data: [
        {
          screenName: "Home",
          p75: 0.08,
          totalCount: 500,
          goodPct: 88,
        },
      ],
      isLoading: false,
      error: null,
    });

    expect(screen.getByText("0.08")).toBeInTheDocument();
  });

  it("should sort data by count descending", () => {
    renderComponent({
      vitalName: "LCP",
      data: mockData,
      isLoading: false,
      error: null,
    });

    const rows = screen.getAllByRole("row");
    // rows[0] is header, rows[1:] are data
    expect(rows[1]).toHaveTextContent("Home");
    expect(rows[2]).toHaveTextContent("Product");
  });

  it("should limit to 20 rows maximum", () => {
    const largeData = Array.from({ length: 30 }).map((_, i) => ({
      screenName: `Screen ${i}`,
      p75: 2000 + i * 100,
      totalCount: 1000 - i * 10,
      goodPct: 80 - i,
    }));

    renderComponent({
      vitalName: "LCP",
      data: largeData,
      isLoading: false,
      error: null,
    });

    const rows = screen.getAllByRole("row");
    // rows[0] is header + max 20 data rows
    expect(rows.length).toBeLessThanOrEqual(21);
  });
});
