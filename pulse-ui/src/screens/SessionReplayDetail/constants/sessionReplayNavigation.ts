import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

/** Query key for “opened from Critical Interaction → View Sessions”. */
export const SESSION_REPLAY_FROM_QUERY_KEY = "from";

/** Value paired with {@link SESSION_REPLAY_FROM_QUERY_KEY} for that flow. */
export const SESSION_REPLAY_FROM_CRITICAL_INTERACTION_VALUE =
  "critical-interaction";

export function sessionReplayFromCriticalInteractionQueryString(): string {
  return `${SESSION_REPLAY_FROM_QUERY_KEY}=${SESSION_REPLAY_FROM_CRITICAL_INTERACTION_VALUE}`;
}

/**
 * Mock-only: align player chrome with the default session-replay hero (Android bezel)
 * when the user arrived from Interaction details → View Sessions. Snapshot blobs are unchanged.
 */
export function applyCriticalInteractionMockReplayPresentation(
  data: SessionDetailData,
): SessionDetailData {
  return {
    ...data,
    platform: "Android",
    device: "Pixel 8",
    os: "14",
  };
}
