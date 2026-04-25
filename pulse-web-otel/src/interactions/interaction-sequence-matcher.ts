/**
 * Android parity: InteractionUtil.matchSequence + buildPulseInteraction
 * (pulse-android-otel/instrumentation/interaction/core).
 */
import type { InteractionConfig } from "./interaction-models";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-prop-keys";
import { INTERACTION_TIME_CATEGORY } from "../constants/interactions/interaction-time-category";
import type {
  InteractionBuildError,
  InteractionErrorCode,
  InteractionLocalEvent,
  InteractionRunningStatus,
  MatchResult,
  PulseInteraction,
} from "../types/interactions/interaction-runtime";
import {
  localEventMatchesConfigEvent,
  localMatchesAnyEvent,
} from "../utils/interactions/event-matching";
import { globalBlacklistAsEvents } from "../utils/interactions/interaction-events";

function interactionErrorMessage(error: InteractionBuildError): string {
  switch (error.type) {
    case "timeout":
      return error.timeoutExpectedEventName != null
        ? `Timed out while waiting for event "${error.timeoutExpectedEventName}".`
        : "Timed out before the next expected event arrived.";
    case "sequence_violation":
      return error.sequenceViolationExpectedEventName != null &&
        error.sequenceViolationReceivedEventName != null
        ? `Expected event "${error.sequenceViolationExpectedEventName}", received "${error.sequenceViolationReceivedEventName}".`
        : "An event did not match the next expected event in this interaction.";
    default:
      return "Interaction error.";
  }
}

function getUpTimeIndex(
  timeDifferenceInMs: number,
  lowerLimit: number,
  upperLimit: number,
): number {
  return (
    1.0 - (1.0 * (timeDifferenceInMs - lowerLimit)) / (upperLimit - lowerLimit)
  );
}

function getEventsBetween(
  markers: readonly InteractionLocalEvent[],
  startInNanoInclusive: number,
  endInNanoInclusive: number,
): InteractionLocalEvent[] {
  return markers.filter(
    (m) =>
      m.timeInNano >= startInNanoInclusive &&
      m.timeInNano <= endInNanoInclusive,
  );
}

function computeInteractionTimeSpanInNanos(
  events: readonly InteractionLocalEvent[],
  timeOutInMs: number,
  errorType: InteractionErrorCode | null,
): [number, number] | null {
  if (errorType != null) {
    if (events.length === 0) return null;
    const firstNs = events[0]!.timeInNano;
    const lastNs = events[events.length - 1]!.timeInNano;
    const thresholdNs = timeOutInMs * 1_000_000;
    if (errorType === "timeout") {
      return [firstNs, firstNs + thresholdNs + (lastNs - firstNs)];
    }
    return [firstNs, lastNs];
  }
  if (events.length === 0) return null;
  if (events.length === 1) {
    const t = events[0]!.timeInNano;
    return [t, t + timeOutInMs * 1_000_000];
  }
  return [events[0]!.timeInNano, events[events.length - 1]!.timeInNano];
}

export function buildPulseInteraction(
  interactionId: string,
  interactionConfig: InteractionConfig,
  events: readonly InteractionLocalEvent[],
  localMarkers: readonly InteractionLocalEvent[],
  error?: InteractionBuildError | null,
): PulseInteraction {
  if (events.length === 0) {
    throw new Error("buildPulseInteraction requires at least one event");
  }
  const interactionName = interactionConfig.name;
  const interactionConfigId = interactionConfig.id;
  const lastEventTimeInNano = events[events.length - 1]!.timeInNano;
  const errorType = error?.type ?? null;

  const errorMessage = error != null ? interactionErrorMessage(error) : null;

  let timeDifferenceInNano: number | null = null;
  let timeCategory: string | null = null;
  let upTimeIndex: number | null = null;

  if (errorType == null) {
    timeDifferenceInNano =
      events[events.length - 1]!.timeInNano - events[0]!.timeInNano;
    const timeDifferenceInMs = timeDifferenceInNano / 1_000_000;
    const lowerLimitInMs = interactionConfig.uptimeLowerLimitInMs;
    const midLimitInMs = interactionConfig.uptimeMidLimitInMs;
    const upperLimitInMs = interactionConfig.uptimeUpperLimitInMs;

    if (timeDifferenceInMs <= lowerLimitInMs) {
      upTimeIndex = 1.0;
      timeCategory = INTERACTION_TIME_CATEGORY.EXCELLENT;
    } else if (timeDifferenceInMs <= midLimitInMs) {
      upTimeIndex = getUpTimeIndex(
        timeDifferenceInMs,
        lowerLimitInMs,
        upperLimitInMs,
      );
      timeCategory = INTERACTION_TIME_CATEGORY.GOOD;
    } else if (timeDifferenceInMs <= upperLimitInMs) {
      upTimeIndex = getUpTimeIndex(
        timeDifferenceInMs,
        lowerLimitInMs,
        upperLimitInMs,
      );
      timeCategory = INTERACTION_TIME_CATEGORY.AVERAGE;
    } else {
      upTimeIndex = 0.0;
      timeCategory = INTERACTION_TIME_CATEGORY.POOR;
    }
  }

  const timeInMsDiffPair = computeInteractionTimeSpanInNanos(
    events,
    interactionConfig.thresholdInMs,
    errorType,
  );

  const markerSlice =
    timeInMsDiffPair != null
      ? getEventsBetween(localMarkers, timeInMsDiffPair[0], timeInMsDiffPair[1])
      : [...localMarkers];

  const props: Record<string, unknown> = {
    [INTERACTION_PROP_KEYS.NAME]: interactionName,
    [INTERACTION_PROP_KEYS.CONFIG_ID]: interactionConfigId,
    [INTERACTION_PROP_KEYS.LAST_EVENT_TIME_IN_NANO]: lastEventTimeInNano,
    [INTERACTION_PROP_KEYS.LOCAL_EVENTS]: [...events],
    [INTERACTION_PROP_KEYS.MARKER_EVENTS]: markerSlice,
    [INTERACTION_PROP_KEYS.APDEX_SCORE]: upTimeIndex,
    [INTERACTION_PROP_KEYS.USER_CATEGORY]: timeCategory,
    [INTERACTION_PROP_KEYS.TIME_TO_COMPLETE_IN_NANO]: timeDifferenceInNano,
    [INTERACTION_PROP_KEYS.IS_ERROR]: errorType != null,
    [INTERACTION_PROP_KEYS.ERROR_TYPE]: errorType ?? undefined,
    [INTERACTION_PROP_KEYS.ERROR_MESSAGE]: errorMessage ?? undefined,
  };

  return {
    id: interactionId,
    name: interactionName,
    props,
  };
}

/**
 * Android InteractionUtil.matchSequence — same control flow (continue skips local index increment).
 */
export function matchInteractionSequence(
  ongoingMatchInteractionId: string,
  localEvents: readonly InteractionLocalEvent[],
  localMarkers: readonly InteractionLocalEvent[],
  interactionConfig: InteractionConfig,
): MatchResult | null {
  const stepWiseTimeInNano: InteractionLocalEvent[] = [];
  let configEventIndex = 0;
  let isMatchOnGoing = false;

  const resetMatching = (): void => {
    stepWiseTimeInNano.length = 0;
    configEventIndex = 0;
    isMatchOnGoing = false;
  };

  const globalEvents = globalBlacklistAsEvents(
    interactionConfig.globalBlacklistedEvents,
  );

  let newInteractionStatus: MatchResult | null = null;
  let localEventIndex = 0;

  while (localEventIndex < localEvents.length) {
    if (configEventIndex >= interactionConfig.events.length) {
      break;
    }
    const localEvent = localEvents[localEventIndex]!;

    if (isMatchOnGoing && localMatchesAnyEvent(localEvent, globalEvents)) {
      return {
        shouldTakeFirstEvent: false,
        shouldResetList: true,
        interactionStatus: { kind: "no_ongoing", old: null },
      };
    }

    const configEvent = interactionConfig.events[configEventIndex]!;
    const isMatch = localEventMatchesConfigEvent(localEvent, configEvent);

    if (isMatch) {
      if (configEvent.isBlacklisted) {
        newInteractionStatus = {
          shouldTakeFirstEvent: false,
          shouldResetList: true,
          interactionStatus: { kind: "no_ongoing", old: null },
        };
      } else {
        stepWiseTimeInNano.push(localEvent);
        configEventIndex++;
        const eventsSize = interactionConfig.events.length;

        if (configEventIndex === eventsSize) {
          isMatchOnGoing = false;
          newInteractionStatus = {
            shouldTakeFirstEvent: false,
            shouldResetList: true,
            interactionStatus: {
              kind: "ongoing",
              index: configEventIndex - 1,
              interactionId: ongoingMatchInteractionId,
              interactionConfig,
              interaction: buildPulseInteraction(
                ongoingMatchInteractionId,
                interactionConfig,
                stepWiseTimeInNano,
                localMarkers,
                null,
              ),
            },
          };
        } else {
          isMatchOnGoing = true;
          newInteractionStatus = {
            shouldTakeFirstEvent: false,
            shouldResetList: false,
            interactionStatus: {
              kind: "ongoing",
              index: configEventIndex - 1,
              interactionId: ongoingMatchInteractionId,
              interactionConfig,
              interaction: null,
            },
          };
        }
      }
    } else if (configEvent.isBlacklisted) {
      configEventIndex++;
      continue;
    } else if (isMatchOnGoing) {
      isMatchOnGoing = false;
      newInteractionStatus = {
        shouldTakeFirstEvent: true,
        shouldResetList: true,
        interactionStatus: {
          kind: "ongoing",
          index: configEventIndex - 1,
          interactionId: ongoingMatchInteractionId,
          interactionConfig,
          interaction: buildPulseInteraction(
            ongoingMatchInteractionId,
            interactionConfig,
            stepWiseTimeInNano,
            localMarkers,
            {
              type: "sequence_violation",
              sequenceViolationExpectedEventName: configEvent.name,
              sequenceViolationReceivedEventName: localEvent.name,
            },
          ),
        },
      };
    } else {
      newInteractionStatus = null;
    }

    localEventIndex++;
  }

  if (newInteractionStatus?.shouldResetList) {
    resetMatching();
  }

  return newInteractionStatus;
}
