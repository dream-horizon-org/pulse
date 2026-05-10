import { render, screen, fireEvent } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { VitalsFilters } from "./components/VitalsFilters";
import { ISSUE_TYPES } from "./AppVitals.constants";

describe("AppVitals - Web Vitals Integration", () => {
  const renderComponent = (props: any) =>
    render(
      <MantineProvider>
        <VitalsFilters {...props} />
      </MantineProvider>
    );

  it("should render Web Vitals segment in SegmentedControl", () => {
    renderComponent({
      issueType: ISSUE_TYPES.CRASHES,
      onIssueTypeChange: jest.fn(),
      stats: { crashes: 10, anrs: 5, nonFatals: 3 },
    });

    expect(screen.getByText("Web Vitals")).toBeInTheDocument();
  });

  it("should call onIssueTypeChange with webVitals when clicked", () => {
    const onIssueTypeChange = jest.fn();

    renderComponent({
      issueType: ISSUE_TYPES.CRASHES,
      onIssueTypeChange,
      stats: { crashes: 10, anrs: 5, nonFatals: 3 },
    });

    const webVitalsButton = screen.getByText("Web Vitals");
    fireEvent.click(webVitalsButton);

    expect(onIssueTypeChange).toHaveBeenCalledWith(ISSUE_TYPES.WEB_VITALS);
  });

  it("should render all segment options including Web Vitals", () => {
    renderComponent({
      issueType: ISSUE_TYPES.CRASHES,
      onIssueTypeChange: jest.fn(),
      stats: { crashes: 10, anrs: 5, nonFatals: 3 },
    });

    expect(screen.getByText("Crashes")).toBeInTheDocument();
    expect(screen.getByText("ANRs")).toBeInTheDocument();
    expect(screen.getByText("Non-Fatal")).toBeInTheDocument();
    expect(screen.getByText("Web Vitals")).toBeInTheDocument();
  });

  it("should have WEB_VITALS constant defined", () => {
    expect(ISSUE_TYPES.WEB_VITALS).toBe("webVitals");
  });
});
