import type { PulseMockScreenName } from "../../../../mocks/mockPulseProjectRegistry";
import { isEcommerceMockThemeEnabled } from "../../../../mocks/mockEcommerceTheme";
import { ECOMMERCE_RCA_HEATMAP_BY_INTERACTION } from "./ecommerceRcaHeatmapLinks";

export type RcaHeatmapTarget = {
  screenName: PulseMockScreenName;
  /** Short line for the card (where taps / RCA focus) */
  label: string;
};

/**
 * Primary screen(s) for heatmap deep links from Interaction → Root Cause.
 * Maps critical interaction → screen where the gesture/API surface lives in mock Pulse.
 */
const RCA_HEATMAP_BY_INTERACTION: Record<string, RcaHeatmapTarget[]> = {
  JoinContestButtonClick: [
    {
      screenName: "ProductListScreen",
      label: "Contest list — Join CTA & contest rows",
    },
  ],
  ContestListAPIFetch: [
    {
      screenName: "ProductListScreen",
      label: "Contest list — list load & refresh",
    },
  ],
  SaveTeamButtonClick: [
    {
      screenName: "ProductDetailScreen",
      label: "Squad / player selection — Save team",
    },
  ],
  PaymentSubmitClick: [
    {
      screenName: "CheckoutFormScreen",
      label: "Checkout — pay / entry fee",
    },
  ],
  WalletBalanceFetch: [
    { screenName: "CartScreen", label: "Wallet strip & cart" },
  ],
  LeaderboardRefreshTap: [
    { screenName: "OrderListScreen", label: "Leaderboard / contests tab" },
  ],
  ProfileSaveClick: [{ screenName: "ProfileScreen", label: "Profile — save" }],
  NotificationTap: [
    {
      screenName: "NotificationsScreen",
      label: "Notifications inbox",
    },
  ],
  FilterApplyTap: [
    {
      screenName: "SearchResultsScreen",
      label: "Filters & results",
    },
  ],
};

export function getRcaHeatmapTargets(
  interactionName: string | null | undefined,
): RcaHeatmapTarget[] {
  const key = interactionName?.trim() ?? "";
  if (!key) return [];
  if (isEcommerceMockThemeEnabled()) {
    return ECOMMERCE_RCA_HEATMAP_BY_INTERACTION[key] ?? [];
  }
  return RCA_HEATMAP_BY_INTERACTION[key] ?? [];
}

/** Deep link to Screen detail → Heatmap tab (optionally preserves custom UTC range). */
export function buildScreenHeatmapUrl(
  projectId: string,
  screenName: string,
  startTime?: string | null,
  endTime?: string | null,
): string {
  const path = `/projects/${encodeURIComponent(projectId)}/screens/${encodeURIComponent(screenName)}`;
  const q = new URLSearchParams();
  q.set("tab", "heatmap");
  const s = startTime?.trim();
  const e = endTime?.trim();
  if (s && e) {
    q.set("quickDateFilter", "-1");
    q.set("startDate", s);
    q.set("endDate", e);
  }
  return `${path}?${q.toString()}`;
}
