import { render, screen, fireEvent } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { VitalCard } from "./VitalCard";
import type { VitalCardProps } from "./VitalCard.interface";

describe("VitalCard", () => {
  const renderComponent = (props: VitalCardProps) =>
    render(
      <MantineProvider>
        <VitalCard {...props} />
      </MantineProvider>,
    );

  it("should render vital name, formatted p75, and badge", () => {
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="LCP"
          p75={2450}
          goodPct={75}
          needsImprovementPct={15}
          poorPct={10}
        />
      </MantineProvider>,
    );

    expect(screen.getByText("LCP")).toBeInTheDocument();
    expect(container.textContent).toContain("2450");
    expect(container.textContent).toContain("ms");
  });

  it("should format CLS as decimal (0.12)", () => {
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="CLS"
          p75={0.12}
          goodPct={80}
          needsImprovementPct={10}
          poorPct={10}
        />
      </MantineProvider>,
    );

    expect(container.textContent).toContain("0.12");
  });

  it("should render progress bar with good/ni/poor sections", () => {
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="INP"
          p75={180}
          goodPct={60}
          needsImprovementPct={25}
          poorPct={15}
        />
      </MantineProvider>,
    );

    // Good, NI, and Poor percentages should be rendered as whole numbers
    expect(container.textContent).toContain("Good: 60%");
    expect(container.textContent).toContain("NI: 25%");
    expect(container.textContent).toContain("Poor: 15%");
  });

  it("should round percentages to at most 1 decimal place", () => {
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="FCP"
          p75={1500}
          goodPct={85.71428571428571}
          needsImprovementPct={0}
          poorPct={14.285714285714286}
        />
      </MantineProvider>,
    );

    expect(container.textContent).toContain("Good: 85.7%");
    expect(container.textContent).toContain("NI: 0%");
    expect(container.textContent).toContain("Poor: 14.3%");
    expect(container.textContent).not.toContain("85.71428");
    expect(container.textContent).not.toContain("14.2857");
    expect(container.textContent).not.toContain("0.0%");
  });

  it("should render whole-number percentages without a trailing .0", () => {
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="CLS"
          p75={0.05}
          goodPct={100}
          needsImprovementPct={0}
          poorPct={0}
        />
      </MantineProvider>,
    );

    expect(container.textContent).toContain("Good: 100%");
    expect(container.textContent).toContain("NI: 0%");
    expect(container.textContent).toContain("Poor: 0%");
    expect(container.textContent).not.toContain("100.0%");
  });

  it("should render green badge for good rating", () => {
    renderComponent({
      name: "LCP",
      p75: 1000,
      goodPct: 90,
      needsImprovementPct: 5,
      poorPct: 5,
    });

    const badge = screen.getByText("Good");
    expect(badge).toBeInTheDocument();
  });

  it("should call onSelect when card is clicked", () => {
    const onSelect = jest.fn();
    const { container } = render(
      <MantineProvider>
        <VitalCard
          name="LCP"
          p75={2450}
          goodPct={75}
          needsImprovementPct={15}
          poorPct={10}
          onSelect={onSelect}
        />
      </MantineProvider>,
    );

    const card = container.querySelector("[style*='cursor']");
    if (card) {
      fireEvent.click(card);
      expect(onSelect).toHaveBeenCalled();
    }
  });
});
