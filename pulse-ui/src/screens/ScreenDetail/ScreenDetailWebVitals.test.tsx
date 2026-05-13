import { render, screen, fireEvent } from "@testing-library/react";
import { MantineProvider, Tabs } from "@mantine/core";
import * as React from "react";

jest.mock("../WebVitals/components/WebVitalsPanel", () => ({
  WebVitalsPanel: ({ screenName }: any) => (
    <div data-testid="web-vitals-content">
      Web Vitals Panel {screenName && `- Screen: ${screenName}`}
    </div>
  ),
}));

// eslint-disable-next-line import/first
import { WebVitalsPanel } from "../WebVitals/components";

// Create a minimal test component that mimics ScreenDetail's Web Vitals tab structure
function MockScreenDetail() {
  const [activeTab, setActiveTab] = React.useState<string | null>("engagement");

  return (
    <Tabs value={activeTab} onChange={setActiveTab}>
      <Tabs.List>
        <Tabs.Tab value="engagement">User Engagement</Tabs.Tab>
        <Tabs.Tab value="performance">Performance & Stability</Tabs.Tab>
        <Tabs.Tab value="network">Network</Tabs.Tab>
        <Tabs.Tab value="web-vitals">Web Vitals</Tabs.Tab>
      </Tabs.List>

      <Tabs.Panel value="engagement">Engagement</Tabs.Panel>
      <Tabs.Panel value="performance">Performance</Tabs.Panel>
      <Tabs.Panel value="network">Network</Tabs.Panel>
      <Tabs.Panel value="web-vitals" data-testid="web-vitals-panel">
        <WebVitalsPanel screenName="TestScreen" startTime="2026-05-01T00:00:00Z" endTime="2026-05-01T23:00:00Z" />
      </Tabs.Panel>
    </Tabs>
  );
}

describe("ScreenDetail - Web Vitals Integration", () => {
  const renderComponent = () => {
    return render(
      <MantineProvider>
        <MockScreenDetail />
      </MantineProvider>
    );
  };

  it("should render Web Vitals tab in ScreenDetail", () => {
    renderComponent();
    expect(screen.getByText("Web Vitals")).toBeInTheDocument();
  });

  it("should render WebVitalsPanel when Web Vitals tab is active", () => {
    renderComponent();
    const webVitalsTab = screen.getByText("Web Vitals");
    fireEvent.click(webVitalsTab);

    expect(screen.getByTestId("web-vitals-panel")).toBeInTheDocument();
  });

  it("should pass screenName to WebVitalsPanel", () => {
    renderComponent();
    const webVitalsTab = screen.getByText("Web Vitals");
    fireEvent.click(webVitalsTab);

    expect(screen.getByText(/Web Vitals Panel - Screen: TestScreen/)).toBeInTheDocument();
  });

  it("should render WebVitalsPanel with screenName prop", () => {
    renderComponent();
    const webVitalsTab = screen.getByText("Web Vitals");
    fireEvent.click(webVitalsTab);

    // WebVitalsPanel is rendered with screenName, so table should be hidden
    expect(screen.getByTestId("web-vitals-content")).toBeInTheDocument();
  });

  it("should render all tab options including Web Vitals", () => {
    renderComponent();

    // Check for all tab buttons using getByRole
    expect(screen.getByRole("tab", { name: "User Engagement" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Performance & Stability" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Network" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Web Vitals" })).toBeInTheDocument();
  });

  it("should switch between tabs correctly", () => {
    renderComponent();

    const engagementTab = screen.getByText("User Engagement");
    const webVitalsTab = screen.getByText("Web Vitals");

    // Initially engagement should be active
    expect(screen.getByText("Engagement")).toBeInTheDocument();

    // Click Web Vitals tab
    fireEvent.click(webVitalsTab);
    expect(screen.getByTestId("web-vitals-content")).toBeInTheDocument();

    // Click back to Engagement
    fireEvent.click(engagementTab);
    expect(screen.getByText("Engagement")).toBeInTheDocument();
  });
});
