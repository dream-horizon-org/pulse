import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../constants";
import type { InteractionDiscoverySuggestion } from "../../hooks/useGetInteractionDiscoveries";
import type { CriticalInteractionFormRequestBodyParams } from "../createJob";

export function buildCreateInteractionBodyFromDiscovery(
  suggestion: InteractionDiscoverySuggestion,
): CriticalInteractionFormRequestBodyParams {
  return {
    name: `${suggestion.startEvent}_to_${suggestion.endEvent}`,
    description: suggestion.description,
    uptimeLowerLimitInMs: parseInt(
      CRITICAL_INTERACTION_FORM_CONSTANTS.LOWER_THRESHOLD_VALUE,
      10,
    ),
    uptimeMidLimitInMs: parseInt(
      CRITICAL_INTERACTION_FORM_CONSTANTS.MIDDLE_THRESHOLD_VALUE,
      10,
    ),
    uptimeUpperLimitInMs: parseInt(
      CRITICAL_INTERACTION_FORM_CONSTANTS.UPPER_THRESHOLD_VALUE,
      10,
    ),
    thresholdInMs: parseInt(
      CRITICAL_INTERACTION_FORM_CONSTANTS.DEFAULT_INTERACTION_THRESHOLD,
      10,
    ),
    events: [
      {
        name: suggestion.startEvent,
        props: [],
        isBlacklisted: false,
      },
      {
        name: suggestion.endEvent,
        props: [],
        isBlacklisted: false,
      },
    ],
    globalBlacklistedEvents: [],
  };
}
