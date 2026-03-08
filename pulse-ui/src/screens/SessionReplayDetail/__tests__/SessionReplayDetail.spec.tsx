import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { SessionReplayDetail } from "../SessionReplayDetail";
import * as sessionReplayImages from "../../../services/sessionReplay/sessionReplayImages";

jest.mock("../../../services/sessionReplay/sessionReplayImages", () => ({
  getSessionReplayImages: jest.fn(),
}));

// Avoid pulling in echarts/CriticalInteractionDetails via DetailsSidebar -> useGetSpanDetails -> constants
jest.mock("../../SessionTimeline/components/DetailsSidebar", () => ({
  DetailsSidebar: () => null,
}));
jest.mock("../components/SessionTabs", () => ({
  SessionTabs: () => null,
}));
jest.mock("../components/SessionPlayerSection", () => ({
  SessionPlayerSection: () => null,
}));
jest.mock("../components/RawSessionEventsSection", () => ({
  RawSessionEventsSection: () => null,
}));
jest.mock("../components/SessionTimelineSection", () => ({
  SessionTimelineSection: () => null,
}));

const mockGetSessionReplayImages =
  sessionReplayImages.getSessionReplayImages as jest.MockedFunction<
    typeof sessionReplayImages.getSessionReplayImages
  >;

const renderWithProvider = (
  sessionId: string,
  component: React.ReactElement = (
    <SessionReplayDetail />
  ),
) => {
  return render(
    <MantineProvider>
      <MemoryRouter
        initialEntries={[`/session-replay/sessions/${sessionId}`]}
        initialIndex={0}
      >
        <Routes>
          <Route
            path="/session-replay/sessions/:sessionId"
            element={component}
          />
        </Routes>
      </MemoryRouter>
    </MantineProvider>,
  );
};

describe("SessionReplayDetail", () => {
  beforeEach(() => {
    mockGetSessionReplayImages.mockResolvedValue([]);
  });

  it("renders without crashing", () => {
    renderWithProvider("test-session-123");
  });

  it("renders session header with back button", () => {
    renderWithProvider("test-session-123");
    expect(screen.getByRole("button", { name: /back/i })).toBeInTheDocument();
  });

  it("uses session id from route params", () => {
    renderWithProvider("my-session-id");
    // Session data is derived via getMockSessionDetail(sessionId); header shows sessionId
    expect(screen.getByText("my-session-id")).toBeInTheDocument();
  });

  it("loads replay images for session", () => {
    renderWithProvider("test-session-123");
    expect(mockGetSessionReplayImages).toHaveBeenCalledWith(
      "test-session-123",
      expect.any(Date),
      10,
    );
  });
});
