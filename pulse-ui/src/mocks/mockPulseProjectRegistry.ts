import {
  ECOMMERCE_PULSE_MOCK_INTERACTION_NAMES,
  ECOMMERCE_SESSION_REPLAY_DETAIL_INTERACTION_ORDER,
} from "./ecommercePulseMockRegistry";
import { isEcommerceMockThemeEnabled } from "./mockEcommerceTheme";

/**
 * Single registry for `proj-mock-1` (and mock Pulse) **screens** and **interactions**
 * so Session Replay mocks stay aligned with:
 * - Interactions: `/projects/:projectId/interactions` — `MockDataStore.initializeJobs()` (`PULSE_MOCK_INTERACTION_NAMES`)
 * - Screens: `/projects/:projectId/screens` and `DataQueryMockGeneratorV2.getGroupValues("screen_name")` — `PULSE_MOCK_SCREEN_NAMES`
 *
 * When `REACT_APP_ECOMMERCE_MOCK_THEME=true`, use {@link getActivePulseMockInteractionNames} /
 * {@link getActiveSessionReplayDetailInteractionOrder} so session replay + data query filters match jobs.
 *
 * Do not invent ad-hoc interaction or screen names in session mocks — import from here.
 */

/** Mirrors `MockDataStore.initializeJobs()` interactionName values (order = job id 1–12). */
export const PULSE_MOCK_INTERACTION_NAMES = [
  "JoinContestButtonClick",
  "SaveTeamButtonClick",
  "PlayerSelectTap",
  "ContestListAPIFetch",
  "PaymentSubmitClick",
  "WalletBalanceFetch",
  "MatchScheduleAPICall",
  "LeaderboardRefreshTap",
  "ProfileSaveClick",
  "NotificationTap",
  "FilterApplyTap",
  "LiveScoreRefresh",
] as const;

export type PulseMockInteractionName =
  (typeof PULSE_MOCK_INTERACTION_NAMES)[number];

/**
 * Screen names for mock Pulse — same set as `dataQueryMockGenerator.ts` predefined `screen_name`
 * group values (Screens dashboard + data query filters).
 */
export const PULSE_MOCK_SCREEN_NAMES = [
  "HomeScreen",
  "ProductListScreen",
  "ProductDetailScreen",
  "CheckoutFormScreen",
  "PaymentScreen",
  "ProfileScreen",
  "SearchResultsScreen",
  "OrderListScreen",
  "CartScreen",
  "WishlistScreen",
  "SettingsScreen",
  "NotificationsScreen",
] as const;

export type PulseMockScreenName = (typeof PULSE_MOCK_SCREEN_NAMES)[number];

/** Default three rows for Session Detail → Interactions (join → team → pay). */
export const SESSION_REPLAY_DETAIL_INTERACTION_ORDER: readonly PulseMockInteractionName[] =
  ["JoinContestButtonClick", "SaveTeamButtonClick", "PaymentSubmitClick"];

/** Interaction filter values + sort order for mocks (theme-aware). */
export function getActivePulseMockInteractionNames(): readonly string[] {
  return isEcommerceMockThemeEnabled()
    ? ECOMMERCE_PULSE_MOCK_INTERACTION_NAMES
    : PULSE_MOCK_INTERACTION_NAMES;
}

/** Session detail API default three interaction rows (theme-aware). */
export function getActiveSessionReplayDetailInteractionOrder(): readonly string[] {
  return isEcommerceMockThemeEnabled()
    ? ECOMMERCE_SESSION_REPLAY_DETAIL_INTERACTION_ORDER
    : SESSION_REPLAY_DETAIL_INTERACTION_ORDER;
}
