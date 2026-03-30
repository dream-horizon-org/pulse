import { isEcommerceMockThemeEnabled } from "../../../../mocks/mockEcommerceTheme";

export type RootCauseMockRelatedSession = {
  sessionId: string;
  duration: string;
  relativeTime: string;
  device: string;
  failureSummary: string;
};

/** IDs match `mockSessionReplayScenarios` curated RCA sessions. */
export function getRootCauseMockRelatedSessions(): RootCauseMockRelatedSession[] {
  if (isEcommerceMockThemeEnabled()) {
    return [
      {
        sessionId: "sess_rca_join_mock_001",
        duration: "3:18",
        relativeTime: "45 min ago",
        device: "Android 13 · App 4.0.0 · Pixel 8",
        failureSummary:
          "Add-to-cart failure on PDP, then ANR on cart refresh—pair with Product detail heatmap and Browse → cart journey; matches Android 4.0.0 + OS 13 segment.",
      },
      {
        sessionId: "sess_rca_join_mock_002",
        duration: "4:05",
        relativeTime: "1 hr ago",
        device: "iOS 17.4 · App 4.2.0 · iPhone 15 Pro",
        failureSummary:
          "Slow category grid and PaymentAuthorize timeout—check PLP heatmap taps near filters and Cart → checkout → payment funnel for iOS 4.2.0.",
      },
    ];
  }
  return [
    {
      sessionId: "sess_rca_join_mock_001",
      duration: "3:18",
      relativeTime: "45 min ago",
      device: "Android 13 · App 4.0.0 · Pixel 8",
      failureSummary:
        "Join API error, then ANR on retry — matches Android 4.0.0 + OS 13 RCA segment.",
    },
    {
      sessionId: "sess_rca_join_mock_002",
      duration: "4:05",
      relativeTime: "1 hr ago",
      device: "iOS 17.4 · App 4.2.0 · iPhone 15 Pro",
      failureSummary:
        "Slow join + interaction error — matches iOS 4.2.0 RCA segment.",
    },
  ];
}
