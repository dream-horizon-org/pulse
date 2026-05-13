import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { BrowserRouter } from "react-router-dom";
import { AppVitals } from "./AppVitals";
import { VitalsFilters } from "./components/VitalsFilters";
import type { VitalsFiltersProps } from "./components/VitalsFilters";
import { ISSUE_TYPES } from "./AppVitals.constants";

jest.mock("./components", () => ({
  ...jest.requireActual("./components"),
  CrashList: () => <div data-testid="crash-list">Crash List</div>,
  ANRList: () => <div data-testid="anr-list">ANR List</div>,
  NonFatalList: () => <div data-testid="nonfatal-list">Non-Fatal List</div>,
  CrashTrendGraph: () => <div data-testid="crash-trend">Crash Trend</div>,
  ANRTrendGraph: () => <div data-testid="anr-trend">ANR Trend</div>,
  NonFatalTrendGraph: () => (
    <div data-testid="nonfatal-trend">Non-Fatal Trend</div>
  ),
  CrashMetricsStats: () => <div data-testid="crash-stats">Crash Stats</div>,
  ANRMetricsStats: () => <div data-testid="anr-stats">ANR Stats</div>,
  AlertStatusStats: () => <div data-testid="alert-stats">Alert Stats</div>,
  AppVitalsFilters: () => <div>Filters</div>,
}));

jest.mock("../WebVitals/components", () => ({
  WebVitalsPanel: (
    props: import("../WebVitals/components/WebVitalsPanel/WebVitalsPanel.interface").WebVitalsPanelProps,
  ) => (
    <div data-testid="web-vitals-panel">
      Web Vitals Panel (start: {props.startTime}, end: {props.endTime})
    </div>
  ),
}));

jest.mock(
  "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker",
  () => {
    return function MockDateTimeRangePicker() {
      return <div>Date Time Picker</div>;
    };
  },
);

jest.mock("./components/ExceptionTable/hooks", () => ({
  useExceptionListData: () => ({
    exceptions: [],
  }),
}));

jest.mock("../../hooks", () => ({
  useAnalytics: () => ({
    trackTabSwitch: jest.fn(),
  }),
  useGetAppStats: () => ({
    data: { totalUsers: 100, totalSessions: 50 },
  }),
}));

describe("AppVitals - Web Vitals Integration", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("VitalsFilters - Web Vitals Segment", () => {
    const renderVitalsFilters = (props: VitalsFiltersProps) =>
      render(
        <MantineProvider>
          <VitalsFilters {...props} />
        </MantineProvider>,
      );

    it("should render Web Vitals segment in SegmentedControl", () => {
      renderVitalsFilters({
        issueType: ISSUE_TYPES.CRASHES,
        onIssueTypeChange: jest.fn(),
        stats: { crashes: 10, anrs: 5, nonFatals: 3 },
      });

      expect(screen.getByText("Web Vitals")).toBeInTheDocument();
    });

    it("should call onIssueTypeChange with webVitals when clicked", () => {
      const onIssueTypeChange = jest.fn();

      renderVitalsFilters({
        issueType: ISSUE_TYPES.CRASHES,
        onIssueTypeChange,
        stats: { crashes: 10, anrs: 5, nonFatals: 3 },
      });

      const webVitalsButton = screen.getByText("Web Vitals");
      fireEvent.click(webVitalsButton);

      expect(onIssueTypeChange).toHaveBeenCalledWith(ISSUE_TYPES.WEB_VITALS);
    });

    it("should render all segment options including Web Vitals", () => {
      renderVitalsFilters({
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

  describe("AppVitals - Web Vitals Panel Rendering", () => {
    const renderAppVitals = () =>
      render(
        <BrowserRouter>
          <MantineProvider>
            <AppVitals />
          </MantineProvider>
        </BrowserRouter>,
      );

    it("should_render_WebVitalsPanel_when_webVitals_segment_selected", async () => {
      renderAppVitals();

      await waitFor(() => {
        expect(screen.getByText("Crashes")).toBeInTheDocument();
      });

      const webVitalsSegment = screen.getByText("Web Vitals");
      fireEvent.click(webVitalsSegment);

      await waitFor(() => {
        expect(screen.getByTestId("web-vitals-panel")).toBeInTheDocument();
      });
    });

    it("should_hide_crash_anr_and_alert_stat_cards_when_web_vitals_tab_selected", async () => {
      renderAppVitals();

      await waitFor(() => {
        expect(screen.getByTestId("crash-stats")).toBeInTheDocument();
      });
      expect(screen.getByTestId("anr-stats")).toBeInTheDocument();
      expect(screen.getByTestId("alert-stats")).toBeInTheDocument();

      fireEvent.click(screen.getByText("Web Vitals"));

      await waitFor(() => {
        expect(screen.getByTestId("web-vitals-panel")).toBeInTheDocument();
      });
      expect(screen.queryByTestId("crash-stats")).not.toBeInTheDocument();
      expect(screen.queryByTestId("anr-stats")).not.toBeInTheDocument();
      expect(screen.queryByTestId("alert-stats")).not.toBeInTheDocument();
    });

    it("should_not_render_WebVitalsPanel_on_other_segments", async () => {
      renderAppVitals();

      await waitFor(() => {
        expect(screen.getByTestId("crash-list")).toBeInTheDocument();
      });

      expect(screen.queryByTestId("web-vitals-panel")).not.toBeInTheDocument();
    });

    it("should_pass_no_screenName_to_WebVitalsPanel", async () => {
      renderAppVitals();

      await waitFor(() => {
        expect(screen.getByText("Crashes")).toBeInTheDocument();
      });

      const webVitalsSegment = screen.getByText("Web Vitals");
      fireEvent.click(webVitalsSegment);

      await waitFor(() => {
        expect(screen.getByTestId("web-vitals-panel")).toBeInTheDocument();
      });

      const panel = screen.getByTestId("web-vitals-panel");
      expect(panel.textContent).not.toContain("screenName");
    });

    it("should_maintain_time_range_filter_store_connection", async () => {
      renderAppVitals();

      await waitFor(() => {
        expect(screen.getByText("Crashes")).toBeInTheDocument();
      });

      const webVitalsSegment = screen.getByText("Web Vitals");
      fireEvent.click(webVitalsSegment);

      await waitFor(() => {
        expect(screen.getByTestId("web-vitals-panel")).toBeInTheDocument();
      });

      const panel = screen.getByTestId("web-vitals-panel");
      expect(panel.textContent).toContain("start:");
      expect(panel.textContent).toContain("end:");
    });
  });
});
