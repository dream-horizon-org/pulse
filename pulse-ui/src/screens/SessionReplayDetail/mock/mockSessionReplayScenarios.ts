/**
 * Single source of truth for REACT_APP_USE_MOCK_SESSION_REPLAY session list + detail.
 *
 * Evidence model (Pulse):
 * - **Issues** = RUM/session signals (crashes, ANRs, network, interaction errors, slow frames, …).
 * - **Impacted interactions** = critical interactions that **failed or degraded** in this session (matches
 *   Session Detail → Interactions tab: failed attempts / poor Apdex). Not “every interaction touched”.
 * - **Quality score** = aggregated session health (0–1). Higher when issues are absent and interactions
 *   succeed; lower with severity; **null** when the session ended before a score could be computed (e.g.
 *   early fatal crash) or telemetry is insufficient.
 *
 * **Interaction names** must match `MockDataStore.initializeJobs()` (Interactions list), including
 * ecommerce theme (`REACT_APP_ECOMMERCE_MOCK_THEME`) via mapped `criticalInteractionNames`.
 * **Journey / impacted screens** use `PULSE_MOCK_SCREEN_NAMES` (Screens dashboard + `screen_name` group values).
 */

import type {
  SessionDetailApiResponse,
  SessionDetailException,
  SessionDetailInteraction,
  SessionItem,
} from "../../../services/sessionReplay/types";
import { isEcommerceMockThemeEnabled } from "../../../mocks/mockEcommerceTheme";
import {
  getActivePulseMockInteractionNames,
  getActiveSessionReplayDetailInteractionOrder,
} from "../../../mocks/mockPulseProjectRegistry";
import {
  getMockPaymentInteractionNameForDetail,
  isMockPaymentInteractionName,
  isMockPrimaryRetailTapInteractionName,
} from "../../../mocks/mockSessionReplayInteractionTheme";
import { buildEcommerceMockSessionItemsFromDefault } from "./ecommerceMockSessionReplayScenarios";

/** Same three rows as Session Detail → Interactions for every curated session (theme-aware). */
export const PULSE_INTERACTION_ORDER =
  getActiveSessionReplayDetailInteractionOrder();

function buildDefaultMockSessionItems(): SessionItem[] {
  const now = Date.now();
  const t = (hoursAgo: number, minutesOffset = 0) =>
    new Date(
      now - hoursAgo * 60 * 60 * 1000 - minutesOffset * 60 * 1000,
    ).toISOString();

  return [
    {
      sessionId: "sess_mock_001",
      startTime: t(0, 17),
      durationMs: 159000,
      user: "user_3456",
      qualityScore: 0.71,
      issues: [
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 1 },
      ],
      platform: "Android",
      spanCount: 187,
      journey: [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "CheckoutFormScreen",
      ],
      impactedScreens: {
        nonFatals: ["ProductListScreen", "ProductDetailScreen"],
      },
      criticalInteractionNames: ["PaymentSubmitClick", "ContestListAPIFetch"],
    },
    {
      sessionId: "sess_mock_002",
      startTime: t(2, 0),
      durationMs: 92000,
      user: null,
      qualityScore: 0.7,
      issues: [
        { type: "NON_FATAL", label: "Non-Fatals", count: 1 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 2 },
      ],
      platform: "iOS",
      spanCount: 94,
      journey: [
        "HomeScreen",
        "OrderListScreen",
        "SearchResultsScreen",
        "ProductListScreen",
      ],
      impactedScreens: { nonFatals: ["SearchResultsScreen"] },
      criticalInteractionNames: ["JoinContestButtonClick"],
    },
    {
      sessionId: "sess_mock_003",
      startTime: t(3, 0),
      durationMs: 310000,
      user: "user_1234",
      qualityScore: 0.97,
      issues: [],
      platform: "Web",
      spanCount: 256,
      journey: [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "CartScreen",
        "CheckoutFormScreen",
        "PaymentScreen",
        "OrderListScreen",
      ],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_004",
      startTime: t(4, 0),
      durationMs: 45000,
      user: "user_5678",
      qualityScore: null,
      issues: [
        { type: "CRASH", label: "Crashes", count: 1 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 3 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 2 },
      ],
      platform: "Android",
      spanCount: 61,
      journey: ["HomeScreen", "ProductListScreen", "SearchResultsScreen"],
      impactedScreens: {
        crashes: ["HomeScreen"],
        nonFatals: ["HomeScreen", "SearchResultsScreen"],
      },
      criticalInteractionNames: ["PaymentSubmitClick"],
    },
    {
      sessionId: "sess_mock_005",
      startTime: t(6, 0),
      durationMs: 210000,
      user: "user_9012",
      qualityScore: 0.62,
      issues: [
        { type: "ANR", label: "ANRs", count: 1 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 1 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 3 },
      ],
      platform: "iOS",
      spanCount: 203,
      journey: [
        "HomeScreen",
        "ProfileScreen",
        "SettingsScreen",
        "NotificationsScreen",
      ],
      impactedScreens: { anrs: ["ProfileScreen"] },
      criticalInteractionNames: ["ProfileSaveClick"],
    },
    {
      sessionId: "sess_mock_006",
      startTime: t(7, 22),
      durationMs: 428000,
      user: "user_2840",
      qualityScore: 0.57,
      issues: [
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 6 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 4 },
        { type: "NON_FATAL", label: "Non-Fatals", count: 2 },
      ],
      platform: "Android",
      spanCount: 312,
      journey: [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "CartScreen",
        "CheckoutFormScreen",
      ],
      impactedScreens: {
        nonFatals: ["ProductDetailScreen", "CartScreen"],
      },
      criticalInteractionNames: [
        "JoinContestButtonClick",
        "PaymentSubmitClick",
      ],
    },
    {
      sessionId: "sess_mock_007",
      startTime: t(9, 5),
      durationMs: 78000,
      user: "vip_user_01",
      qualityScore: 0.78,
      issues: [
        { type: "NETWORK_ERROR", label: "Network Errors", count: 5 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
      ],
      platform: "Web",
      spanCount: 142,
      journey: [
        "HomeScreen",
        "OrderListScreen",
        "SearchResultsScreen",
        "PaymentScreen",
      ],
      impactedScreens: null,
      criticalInteractionNames: ["ContestListAPIFetch"],
    },
    {
      sessionId: "sess_mock_008",
      startTime: t(11, 40),
      durationMs: 12000,
      user: null,
      qualityScore: null,
      issues: [{ type: "CRASH", label: "Crashes", count: 1 }],
      platform: "iOS",
      spanCount: 18,
      journey: ["HomeScreen", "SettingsScreen"],
      impactedScreens: { crashes: ["SettingsScreen"] },
      criticalInteractionNames: ["PaymentSubmitClick"],
    },
    {
      sessionId: "sess_mock_009",
      startTime: t(14, 12),
      durationMs: 602000,
      user: "user_7777",
      qualityScore: 0.86,
      issues: [
        { type: "NON_FATAL", label: "Non-Fatals", count: 4 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 1 },
      ],
      platform: "Android",
      spanCount: 445,
      journey: [
        "HomeScreen",
        "SearchResultsScreen",
        "ProductDetailScreen",
        "NotificationsScreen",
        "PaymentScreen",
      ],
      impactedScreens: { nonFatals: ["PaymentScreen"] },
      criticalInteractionNames: ["SaveTeamButtonClick"],
    },
    {
      sessionId: "sess_mock_010",
      startTime: t(16, 33),
      durationMs: 195000,
      user: "user_1122",
      qualityScore: 0.41,
      issues: [
        { type: "ANR", label: "ANRs", count: 2 },
        { type: "CRASH", label: "Crashes", count: 1 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 3 },
      ],
      platform: "Android",
      spanCount: 88,
      journey: ["HomeScreen", "WishlistScreen", "SearchResultsScreen"],
      impactedScreens: {
        anrs: ["SearchResultsScreen"],
        crashes: ["SearchResultsScreen"],
      },
      criticalInteractionNames: ["LeaderboardRefreshTap", "PaymentSubmitClick"],
    },
    {
      sessionId: "sess_mock_011",
      startTime: t(18, 50),
      durationMs: 67000,
      user: "qa_bot_session",
      qualityScore: 0.99,
      issues: [],
      platform: "Web",
      spanCount: 72,
      journey: ["HomeScreen", "SettingsScreen", "ProfileScreen"],
      impactedScreens: null,
    },
    {
      sessionId: "sess_mock_012",
      startTime: t(22, 8),
      durationMs: 340000,
      user: "user_9999",
      qualityScore: 0.66,
      issues: [
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 2 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "FROZEN_FRAME", label: "Frozen Frames", count: 1 },
      ],
      platform: "iOS",
      spanCount: 198,
      journey: ["HomeScreen", "OrderListScreen", "SearchResultsScreen"],
      impactedScreens: { nonFatals: ["OrderListScreen"] },
      criticalInteractionNames: ["JoinContestButtonClick"],
    },
    /** Curated for Interaction Details → Root Cause → Related Session Replays (aligns with mock RCA segments: Andr 4.0.0+OS13, iOS 4.2.0). */
    {
      sessionId: "sess_rca_join_mock_001",
      startTime: t(0, 45),
      durationMs: 198000,
      user: "user_rca_andr_13",
      qualityScore: 0.38,
      issues: [
        { type: "ANR", label: "ANRs", count: 1 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 2 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 1 },
      ],
      platform: "Android",
      spanCount: 156,
      journey: [
        "HomeScreen",
        "OrderListScreen",
        "SearchResultsScreen",
        "ProductListScreen",
      ],
      impactedScreens: {
        anrs: ["SearchResultsScreen"],
        nonFatals: ["ProductListScreen"],
      },
      criticalInteractionNames: ["JoinContestButtonClick"],
    },
    {
      sessionId: "sess_rca_join_mock_002",
      startTime: t(1, 12),
      durationMs: 245000,
      user: null,
      qualityScore: 0.52,
      issues: [
        { type: "SLOW_INTERACTION", label: "Slow Interactions", count: 4 },
        { type: "NETWORK_ERROR", label: "Network Errors", count: 2 },
        { type: "INTERACTION_ERROR", label: "Interaction Errors", count: 1 },
      ],
      platform: "iOS",
      spanCount: 178,
      journey: [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "OrderListScreen",
      ],
      impactedScreens: {
        nonFatals: ["ProductListScreen", "OrderListScreen"],
      },
      criticalInteractionNames: ["JoinContestButtonClick"],
    },
  ];
}

const _defaultMockSessionItems = buildDefaultMockSessionItems();

export const MOCK_SESSION_ITEMS: SessionItem[] = isEcommerceMockThemeEnabled()
  ? buildEcommerceMockSessionItemsFromDefault(_defaultMockSessionItems)
  : _defaultMockSessionItems;

const MOCK_SESSION_BY_ID = new Map(
  MOCK_SESSION_ITEMS.map((s) => [s.sessionId, s]),
);

function deviceForPlatform(platform: string): {
  device: string;
  osVersion: string;
  appVersion: string;
} {
  const p = platform.toLowerCase();
  if (p === "ios")
    return { device: "iPhone 15 Pro", osVersion: "17.2", appVersion: "2.3.1" };
  if (p === "web")
    return { device: "Chrome", osVersion: "124", appVersion: "1.8.0" };
  return { device: "Pixel 8", osVersion: "14", appVersion: "2.3.1" };
}

function geographyForPlatform(platform: string): string {
  const p = platform.toLowerCase();
  if (p === "web") return "India, Mumbai";
  if (p === "ios") return "India, Delhi NCR";
  return "India, Bengaluru";
}

function interactionSortIndex(name: string): number {
  const order = getActivePulseMockInteractionNames();
  const i = order.indexOf(name);
  return i === -1 ? 999 : i;
}

function buildInteractionsForItem(
  item: SessionItem,
): SessionDetailInteraction[] {
  const impacted = new Set(item.criticalInteractionNames ?? []);
  const detailOrder = getActiveSessionReplayDetailInteractionOrder();
  const names = Array.from(
    new Set([...detailOrder, ...(item.criticalInteractionNames ?? [])]),
  ).sort((a, b) => interactionSortIndex(a) - interactionSortIndex(b));

  return names.map((name) => {
    const failed = impacted.has(name);
    const payFail = isMockPaymentInteractionName(name) && failed;
    return {
      interactionName: name,
      status: failed ? "failed" : "success",
      successCount: failed ? 0 : 1,
      failureCount: failed ? 1 : 0,
      durationMs: payFail ? 30100 : failed ? 2100 : 95,
      apdexScore: failed
        ? isMockPaymentInteractionName(name)
          ? 0
          : 0.22
        : isMockPrimaryRetailTapInteractionName(name)
          ? 0.88
          : 0.91,
    };
  });
}

function buildExceptionsForItem(
  sessionId: string,
  item: SessionItem,
): SessionDetailException[] | undefined {
  const issues = item.issues;
  if (!issues.length) return undefined;

  const out: SessionDetailException[] = [];
  const ts = (ms: number) => Math.min(ms, Math.max(0, item.durationMs - 50));

  if (issues.some((i) => i.type === "CRASH")) {
    out.push({
      traceId: `trace_${sessionId}_crash`,
      spanId: `span_${sessionId}_crash`,
      pulseType: "error",
      timestamp: ts(Math.floor(item.durationMs * 0.45)),
      title: "FatalException: Crash in foreground",
      exceptionStackTrace: isEcommerceMockThemeEnabled()
        ? "java.lang.RuntimeException: CheckoutActivity destroyed during payment handoff\n" +
          "\tat com.pulsemart.checkout.CheckoutActivity.onDestroy(CheckoutActivity.kt:188)\n" +
          "\tat android.app.Activity.performDestroy(Activity.java:9781)"
        : "java.lang.RuntimeException: Activity destroyed during contest entry payment\n" +
          "\tat com.dream11.ui.MainActivity.onDestroy(MainActivity.kt:204)\n" +
          "\tat android.app.Activity.performDestroy(Activity.java:9781)",
    });
  }
  if (issues.some((i) => i.type === "ANR")) {
    out.push({
      traceId: `trace_${sessionId}_anr`,
      spanId: `span_${sessionId}_anr`,
      pulseType: "non_fatal",
      timestamp: ts(Math.floor(item.durationMs * 0.35)),
      title: "ANR: Input dispatching timed out",
      exceptionStackTrace: isEcommerceMockThemeEnabled()
        ? "ANR in com.pulsemart.profile.AccountFragment — main thread blocked 5s+"
        : "ANR in com.dream11.profile.ProfileFragment — main thread blocked 5s+",
    });
  }
  if (issues.some((i) => i.type === "NON_FATAL")) {
    out.push({
      traceId: `trace_${sessionId}_nf`,
      spanId: `span_${sessionId}_nf`,
      pulseType: "non_fatal",
      timestamp: ts(8000),
      title: isEcommerceMockThemeEnabled()
        ? "CartPricingValidationError"
        : "ContestEntryValidationError",
      exceptionStackTrace: isEcommerceMockThemeEnabled()
        ? "java.lang.IllegalStateException: Cart total mismatch after promo apply\n" +
          "\tat com.pulsemart.cart.CartRepository.validateTotals(CartRepository.kt:142)"
        : "java.lang.IllegalStateException: Contest entry fee not locked for selected XI\n" +
          "\tat com.dream11.contest.ContestEntryRepository.validateEntry(ContestEntryRepository.kt:198)",
    });
  }
  if (issues.some((i) => i.type === "INTERACTION_ERROR")) {
    const payName = getMockPaymentInteractionNameForDetail();
    out.push({
      traceId: `trace_${sessionId}_ie`,
      spanId: `span_${sessionId}_ie`,
      pulseType: "non_fatal",
      timestamp: ts(12000),
      title: "InteractionTapTimeout",
      exceptionStackTrace: `Critical interaction exceeded max latency budget (${payName})`,
    });
  }

  return out.length ? out : undefined;
}

function fallbackQualityForDetail(item: SessionItem): number {
  if (item.qualityScore != null) return item.qualityScore;
  if (item.issues.some((i) => i.type === "CRASH")) return 0.18;
  return 0.45;
}

/**
 * Aligns generic session-detail mock with the curated `sess_mock_*` session list evidence.
 */
export function applyMockSessionDetailOverrides(
  sessionId: string,
  base: SessionDetailApiResponse,
): SessionDetailApiResponse {
  const item = MOCK_SESSION_BY_ID.get(sessionId);
  if (!item) return base;

  const start = new Date(item.startTime);
  const end = new Date(start.getTime() + item.durationMs);
  const { device, osVersion, appVersion } = deviceForPlatform(item.platform);
  const plat = item.platform as SessionDetailApiResponse["platform"];

  return {
    ...base,
    sessionId: item.sessionId,
    userId: item.user ?? "anonymous",
    isAnonymous: item.user == null,
    startTime: item.startTime,
    endTime: end.toISOString(),
    duration: item.durationMs,
    platform: plat,
    device,
    osVersion,
    appVersion,
    geography: geographyForPlatform(item.platform),
    quality: fallbackQualityForDetail(item),
    journey: item.journey,
    interactions: buildInteractionsForItem(item),
    exceptions: buildExceptionsForItem(sessionId, item),
  };
}
